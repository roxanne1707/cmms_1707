import api from './api';
import {
  MaintenanceFailures,
  MaintenanceRecoveryTarget,
  MaintenanceReplayResult
} from '../models/owns/notificationFailure';

// Company scope comes only from the authenticated session, never a query parameter.
export const getNotificationFailures = (signal?: AbortSignal) =>
  api.get<MaintenanceFailures>('reliable-events/failures', { signal });

export const isValidRecoveryReason = (reason: string): boolean =>
  reason.trim().length >= 5 && reason.length <= 500;

export const recoverNotification = (
  target: MaintenanceRecoveryTarget,
  reason: string
) => {
  if (!isValidRecoveryReason(reason)) {
    return Promise.reject(
      Object.assign(new Error('Invalid recovery reason'), { status: 400 })
    );
  }
  const path =
    target.type === 'event'
      ? `reliable-events/${encodeURIComponent(target.id)}/replay`
      : `reliable-events/deliveries/${encodeURIComponent(target.id)}/retry`;
  return api.post<MaintenanceReplayResult>(
    path,
    { reason: reason.trim() },
    { suppressConflictNotification: true }
  );
};

export const notificationFailureErrorKey = (
  error: unknown,
  recovery = false
): string => {
  const status = (error as { status?: number })?.status;
  if (status === 401) return 'maintenance_failure_auth_error';
  if (status === 403) return 'maintenance_failure_permission_error';
  if (status === 404)
    return recovery
      ? 'maintenance_failure_missing'
      : 'maintenance_failure_unavailable';
  if (status === 409) return 'maintenance_failure_conflict';
  if (status === 400) return 'maintenance_failure_reason_invalid';
  return recovery
    ? 'maintenance_failure_recovery_unknown'
    : 'maintenance_failure_load_error';
};
