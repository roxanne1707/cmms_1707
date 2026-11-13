import {
  clearPendingCommands,
  isMaintenanceCommand,
  runIdempotentCommand
} from './idempotentCommand';
import { apiUrl } from '../config';

type Options = RequestInit & {
  raw?: boolean;
  headers?: HeadersInit;
  suppressConflictNotification?: boolean;
};

const REFRESH_URL = `${apiUrl}auth/refresh`;

// The token pair identifies a browser session. Only our own refresh may rotate it
// in place; a login, account switch or logout creates a different session object.
// This is a client race guard, not an authorization decision.
type AuthSession = { accessToken: string | null; refreshToken: string | null };
type RefreshFlight = { session: AuthSession; promise: Promise<boolean> };
let observedSession: AuthSession | null = null;
let refreshFlight: RefreshFlight | null = null;

function captureSession(): AuthSession {
  const accessToken = localStorage.getItem('accessToken');
  const refreshToken = localStorage.getItem('refreshToken');
  if (
    !observedSession ||
    observedSession.accessToken !== accessToken ||
    observedSession.refreshToken !== refreshToken
  ) {
    if (observedSession) clearPendingCommands();
    observedSession = { accessToken, refreshToken };
  }
  return observedSession;
}

/** Opaque client identity is stable only across this helper's own token refresh.
 * Used to cancel sockets when a login/account switch replaces the browser session. */
export function getAuthSessionSnapshot(): { identity: object; accessToken: string | null } {
  const session = captureSession();
  return { identity: session, accessToken: session.accessToken };
}

function isCurrentSession(session: AuthSession): boolean {
  return captureSession() === session;
}

function assertRequestCurrent(
  session: AuthSession,
  signal?: AbortSignal
): void {
  if (signal?.aborted) {
    throw Object.assign(new Error('Request aborted'), { name: 'AbortError' });
  }
  if (!isCurrentSession(session)) {
    throw Object.assign(new Error('Authentication session changed'), {
      status: 401
    });
  }
}

function clearTokens(session: AuthSession): void {
  // A late failure from a previous account must not sign out the current account.
  if (!isCurrentSession(session)) return;
  clearPendingCommands();
  localStorage.removeItem('accessToken');
  localStorage.removeItem('refreshToken');
  observedSession = null;
}

async function performRefresh(session: AuthSession): Promise<boolean> {
  if (!session.refreshToken || !isCurrentSession(session)) return false;
  try {
    const response = await fetch(REFRESH_URL, {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ refreshToken: session.refreshToken })
    });
    if (!isCurrentSession(session)) return false;
    if (!response.ok) {
      clearTokens(session);
      return false;
    }
    const data = await response.json();
    // Parsing is asynchronous too: check again immediately before storing tokens.
    if (!isCurrentSession(session)) return false;
    if (!data?.accessToken) {
      clearTokens(session);
      return false;
    }
    localStorage.setItem('accessToken', data.accessToken);
    if (data.refreshToken)
      localStorage.setItem('refreshToken', data.refreshToken);
    // No await between writing storage and updating the observed pair.
    session.accessToken = data.accessToken;
    session.refreshToken = data.refreshToken || session.refreshToken;
    return true;
  } catch {
    clearTokens(session);
    return false;
  }
}

function refreshSession(session: AuthSession): Promise<boolean> {
  if (!isCurrentSession(session)) return Promise.resolve(false);
  if (refreshFlight?.session === session) return refreshFlight.promise;
  const flight = { session, promise: null } as RefreshFlight;
  flight.promise = performRefresh(session).finally(() => {
    // An older account's completion must not erase a newer account's flight.
    if (refreshFlight === flight) refreshFlight = null;
  });
  refreshFlight = flight;
  return flight.promise;
}

export function refreshAccessToken(): Promise<boolean> {
  return refreshSession(captureSession());
}

function isRefreshRequest(url: string): boolean {
  return url.replace(/\/+$/, '').endsWith('/auth/refresh');
}

let onConflictError: (() => void) | null = null;

export function setConflictErrorHandler(handler: () => void): () => void {
  onConflictError = handler;
  return () => {
    onConflictError = null;
  };
}

async function doFetch<T>(
  url: string,
  options: Options,
  retried: boolean,
  session: AuthSession = captureSession()
): Promise<T> {
  assertRequestCurrent(session, options.signal);
  const sentAccessToken = session.accessToken;
  const headers = new Headers(authHeader(false));
  new Headers(options?.headers).forEach((value, key) =>
    headers.set(key, value)
  );
  const response = await fetch(url, { ...options, headers });
  assertRequestCurrent(session, options.signal);
  if (!response.ok) {
    if (response.status === 401 && !retried && !isRefreshRequest(url)) {
      // A delayed 401 may arrive after another request already rotated this session.
      const refreshed =
        session.accessToken !== sentAccessToken && !!session.accessToken
          ? true
          : await refreshSession(session);
      assertRequestCurrent(session, options.signal);
      if (refreshed) return doFetch<T>(url, options, true, session);
    }
    if (response.status === 409) {
      if (onConflictError && !options.suppressConflictNotification)
        onConflictError();
      const conflict = new Error('conflict_error');
      (conflict as any).status = 409;
      throw conflict;
    }
    let body: any = null;
    try {
      body = await response.json();
    } catch {
      body = null;
    }
    const err = new Error(JSON.stringify(body));
    (err as any).status = response.status;
    throw err;
  }
  if (options?.raw) return response as unknown as T;
  const data = await response.json();
  assertRequestCurrent(session, options.signal);
  return data as T;
}

async function api<T>(url: string, options: Options = {}): Promise<T> {
  const session = captureSession();
  if (!isMaintenanceCommand(url, options.method))
    return doFetch<T>(url, options, false, session);
  const supplied = new Headers(options.headers).get('Idempotency-Key');
  if (supplied) return doFetch<T>(url, options, false, session);
  const auth = new Headers(authHeader(false)).get('Authorization') || '';
  // The JWT payload is used only to distinguish local retry sessions, never to grant access.
  const identity = JSON.stringify([
    url,
    options.method,
    options.body,
    commandSession(auth)
  ]);
  return runIdempotentCommand(identity, (key) => {
    const headers = new Headers(options.headers);
    headers.set('Idempotency-Key', key);
    return doFetch<T>(url, { ...options, headers }, false, session);
  });
}

function commandSession(auth: string): string {
  try {
    const payload = auth.split('.')[1];
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(normalized)).sub || auth;
  } catch {
    return auth;
  }
}

function get<T>(url, options?: Options) {
  return api<T>(apiUrl + url, options);
}

function post<T>(url, data, options?: Options, isNotJson?: boolean) {
  return api<T>(apiUrl + url, {
    ...options,
    method: 'POST',
    body: isNotJson ? data : JSON.stringify(data)
  });
}

function patch<T>(url, data, options?: Options) {
  return api<T>(apiUrl + url, {
    ...options,
    method: 'PATCH',
    body: JSON.stringify(data)
  });
}

function deletes<T>(url, options?: Options) {
  return api<T>(apiUrl + url, { ...options, method: 'DELETE' });
}

export function authHeader(publicRoute: boolean): HeadersInit {
  // return authorization header with jwt token
  let accessToken = localStorage.getItem('accessToken');

  if (!publicRoute && accessToken) {
    return {
      Authorization: 'Bearer ' + accessToken,
      Accept: 'application/json',
      'Content-Type': 'application/json'
    };
  } else {
    return {
      Accept: 'application/json',
      'Content-Type': 'application/json'
    };
  }
}

export const getErrorMessage = (
  error: any,
  defaultMessage: string = 'An error occurred'
): string => {
  try {
    const parsed = JSON.parse(error.message);
    return parsed?.message ?? error.message ?? defaultMessage;
  } catch {
    return error.message ?? defaultMessage;
  }
};

export const isNetworkError = (error: any): boolean => {
  if (!error) return false;
  if (error instanceof TypeError) return true;
  if (error?.status === 502) return true;
  const message = String(error?.message ?? '').toLowerCase();
  return (
    message.includes('failed to fetch') ||
    message.includes('networkerror') ||
    message.includes('network error') ||
    message.includes('load failed') ||
    message.includes('connection refused') ||
    message.includes('err_connection_refused')
  );
};

export default { get, patch, post, deletes };
