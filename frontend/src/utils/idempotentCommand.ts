// Uncertain network outcomes retain their operation key. A completed command frees it,
// so a later intentional operation with identical values gets a new identity.
const pending = new Map<string, string>();
let sequence = 0;

export function clearPendingCommands(): void { pending.clear(); }

function newKey(): string {
  const cryptoApi = (globalThis as any).crypto;
  if (cryptoApi?.randomUUID) return cryptoApi.randomUUID();
  // Operation identity only; never used as an authentication credential.
  return `cmms-${Date.now().toString(36)}-${(++sequence).toString(36)}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`;
}

export function isMaintenanceCommand(url: string, method?: string): boolean {
  const path = url.split('?')[0].replace(/\/+$/, '');
  return (method === 'POST' && /\/readings$/.test(path)) ||
    (method === 'POST' && /\/parts\/\d+\/restock$/.test(path)) ||
    (['POST', 'PATCH', 'DELETE'].includes(method || '') && /\/part-quantities(?:\/(?:work-order|purchase-order)\/\d+|\/\d+)?$/.test(path)) ||
    (method === 'PATCH' && /\/purchase-orders\/\d+\/respond$/.test(path));
}

export async function runIdempotentCommand<T>(
  identity: string,
  execute: (key: string) => Promise<T>
): Promise<T> {
  let key = pending.get(identity);
  if (!key) {
    if (pending.size >= 100) throw new Error('Too many commands with unknown outcomes. Retry an existing command before starting another.');
    key = newKey();
    pending.set(identity, key);
  }
  try {
    const result = await execute(key);
    if (pending.get(identity) === key) pending.delete(identity);
    return result;
  } catch (error) {
    const status = (error as any)?.status;
    if (status >= 400 && status < 500 && pending.get(identity) === key) pending.delete(identity);
    // A timeout/connection reset/5xx may occur after commit: keep this key for a retry.
    throw error;
  }
}
