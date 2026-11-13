import { Client, IMessage, ReconnectionTimeMode, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { apiUrl } from '../config';
import { getAuthSessionSnapshot, refreshAccessToken } from './api';

type Listener = { destination: string; receive: (message: IMessage) => void;
  ended?: (reason: Error) => void; wire?: StompSubscription };
export type SessionSocket = {
  readonly connected: boolean;
  subscribe: (destination: string, receive: (message: IMessage) => void,
    ended?: (reason: Error) => void) => { unsubscribe: () => void };
  close: () => void;
};

function tokenInfo(token: string | null): { sub?: string; exp?: number } {
  try { return JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))); }
  catch { return {}; }
}

/** Reconnects read-only subscriptions; never replays the HTTP command that started an import/export. */
export function createSessionSocket(subject: string, onReady?: () => void): SessionSocket {
  const scope = getAuthSessionSnapshot().identity;
  const listeners = new Map<number, Listener>();
  let nextId = 0, generation = 0, failures = 0, protocolFailures = 0;
  let closed = false, restarting = false, refreshing = false, sentToken: string | null = null;
  let timer: ReturnType<typeof setInterval> | undefined;
  const current = () => {
    const snapshot = getAuthSessionSnapshot();
    return snapshot.identity === scope && !!snapshot.accessToken && tokenInfo(snapshot.accessToken).sub === subject;
  };
  const client = new Client({
    webSocketFactory: () => {
      if (closed || !current()) throw new Error('Authentication session changed');
      return new SockJS(`${apiUrl}ws`);
    },
    reconnectDelay: 1000,
    maxReconnectDelay: 15000,
    reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
    connectionTimeout: 10000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    debug: () => {} // STOMP debug frames contain authorization headers.
  });
  const stop = (reason: string) => {
    if (closed) return;
    closed = true; generation++;
    if (timer !== undefined) clearInterval(timer);
    window.removeEventListener('storage', watch);
    window.removeEventListener('focus', watch);
    void client.deactivate({ force: true });
    const pending = Array.from(listeners.values()); listeners.clear();
    pending.forEach(listener => listener.ended?.(new Error(reason)));
  };
  const restart = async () => {
    if (closed || restarting) return;
    restarting = true; generation++;
    try {
      await client.deactivate({ force: true });
      if (!closed && current()) client.activate();
      else stop('Authentication session changed');
    } finally { restarting = false; }
  };
  const refresh = async () => {
    if (refreshing || closed) return;
    refreshing = true;
    try {
      if (!current() || !(await refreshAccessToken()) || !current()) stop('Session revoked or expired');
      else await restart();
    } finally { refreshing = false; }
  };
  const bind = (listener: Listener) => {
    const boundGeneration = generation;
    listener.wire = client.subscribe(listener.destination, message => {
      if (closed || boundGeneration !== generation || !current() ||
        getAuthSessionSnapshot().accessToken !== sentToken) return;
      listener.receive(message);
    });
  };
  client.beforeConnect = async () => {
    if (closed || !current()) { stop('Authentication session changed'); return; }
    let snapshot = getAuthSessionSnapshot();
    const expires = tokenInfo(snapshot.accessToken).exp;
    if (!expires || expires * 1000 <= Date.now() + 30000) {
      if (!(await refreshAccessToken()) || closed || !current()) { stop('Session revoked or expired'); return; }
      snapshot = getAuthSessionSnapshot();
    }
    sentToken = snapshot.accessToken; generation++;
    client.connectHeaders = { token: sentToken };
  };
  client.onConnect = () => {
    if (closed || !current()) { stop('Authentication session changed'); return; }
    if (getAuthSessionSnapshot().accessToken !== sentToken) { void restart(); return; }
    failures = 0;
    listeners.forEach(bind);
    onReady?.();
  };
  client.onWebSocketClose = () => {
    generation++; listeners.forEach(listener => { listener.wire = undefined; });
    if (closed || restarting) return;
    if (!current()) stop('Authentication session changed');
    else if (++failures >= 8) stop('Realtime connection unavailable; reload before retrying the operation');
  };
  client.onStompError = () => {
    if (++protocolFailures >= 3) stop('Realtime authorization rejected; reload after checking the account');
    else void refresh();
  };
  function watch() {
    if (closed) return;
    if (!current()) { stop('Authentication session changed'); return; }
    const snapshot = getAuthSessionSnapshot();
    if (sentToken && snapshot.accessToken !== sentToken) { void restart(); return; }
    const expires = tokenInfo(snapshot.accessToken).exp;
    if (expires && expires * 1000 <= Date.now() + 30000) void refresh();
  }
  const socket: SessionSocket = {
    get connected() { return !closed && current() && client.connected && sentToken === getAuthSessionSnapshot().accessToken; },
    subscribe(destination, receive, ended) {
      if (closed || !current()) throw new Error('Authentication session changed');
      const id = ++nextId, listener: Listener = { destination, receive, ended };
      listeners.set(id, listener);
      if (socket.connected) bind(listener);
      return { unsubscribe: () => {
        listeners.delete(id);
        if (client.connected) listener.wire?.unsubscribe();
      } };
    },
    close: () => stop('Realtime operation cancelled')
  };
  timer = setInterval(watch, 1000);
  window.addEventListener('storage', watch);
  window.addEventListener('focus', watch);
  client.activate();
  return socket;
}
