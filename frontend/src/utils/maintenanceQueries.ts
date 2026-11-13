import api from './api';
import { Company } from '../models/owns/company';
import { UserResponseDTO } from '../models/user';
import { PermissionEntity } from '../models/owns/role';
import { canSeeMaintenancePage } from './maintenanceAccess';
import {
  AssetWorkOrderOverview,
  MaintenanceAssetOption,
  MaintenanceLedgerPage,
  MaintenanceLedgerQuery,
  MaintenancePeriod
} from '../models/owns/maintenanceQuery';

// Navigation hint only. The API rechecks account, seat, subscription and resource scope.
export function canViewMaintenanceQuery(
  user: UserResponseDTO | null,
  company: Company | null,
  ledger = false
): boolean {
  return (
    canSeeMaintenancePage(user, company, ledger ? 'ledger' : 'overview') &&
    user.companyId === company.id
  );
}

export function maintenanceScopeKey(
  user: UserResponseDTO,
  company: Company
): string {
  return JSON.stringify([
    user.id,
    user.companyId,
    company.id,
    user.enabled,
    user.role,
    company.subscription?.subscriptionPlan?.features
  ]);
}

export function defaultMaintenancePeriod(now = new Date()): MaintenancePeriod {
  const end = new Date(now);
  end.setUTCSeconds(0, 0);
  const start = new Date(end.getTime() - 30 * 86400000);
  return {
    start: start.toISOString().slice(0, 16),
    end: end.toISOString().slice(0, 16),
    includeArchived: true,
    includeDemo: false
  };
}

export function parseUtcMinute(value: string): string | null {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value)) return null;
  const date = new Date(`${value}:00.000Z`);
  return Number.isFinite(date.getTime()) &&
    date.toISOString().slice(0, 16) === value
    ? date.toISOString()
    : null;
}

export function maintenancePeriodError(
  draft: MaintenancePeriod
): string | null {
  const start = parseUtcMinute(draft.start);
  const end = parseUtcMinute(draft.end);
  if (!start || !end) return 'maintenance_invalid_date';
  if (start < '1900-01-01T00:00:00.000Z' || end > '2201-01-01T00:00:00.000Z')
    return 'maintenance_date_bounds';
  const duration = Date.parse(end) - Date.parse(start);
  return duration <= 0 || duration > 366 * 86400000
    ? 'maintenance_invalid_range'
    : null;
}

export function maintenancePeriodPayload(
  draft: MaintenancePeriod
): MaintenancePeriod {
  const error = maintenancePeriodError(draft);
  if (error) throw new Error(error);
  return {
    start: parseUtcMinute(draft.start)!,
    end: parseUtcMinute(draft.end)!,
    includeArchived: draft.includeArchived,
    includeDemo: draft.includeDemo
  };
}

export function maintenanceErrorKey(error: any): string {
  switch (error?.status) {
    case 400:
      return 'maintenance_bad_request';
    case 401:
      return 'maintenance_sign_in';
    case 403:
      return 'maintenance_access_denied';
    case 404:
      return 'maintenance_not_found';
    case 503:
      return 'maintenance_overloaded';
    default:
      return 'maintenance_load_failed';
  }
}

export function queryAssetOverview(
  assetId: number,
  companyId: number,
  body: MaintenancePeriod,
  signal?: AbortSignal
) {
  return api.post<AssetWorkOrderOverview>(
    `query/assets/${assetId}/work-order-overview?companyId=${companyId}`,
    body,
    { signal }
  );
}
export function queryMaintenanceLedger(
  companyId: number,
  body: MaintenanceLedgerQuery,
  signal?: AbortSignal
) {
  return api.post<MaintenanceLedgerPage>(
    `query/maintenance-ledger?companyId=${companyId}`,
    body,
    { signal }
  );
}
export function searchMaintenanceAssets(
  term: string,
  companyId: number,
  signal?: AbortSignal
) {
  return api.post<{ content: MaintenanceAssetOption[]; totalElements: number }>(
    'assets/search',
    {
      filterFields: [
        { field: 'company.id', operation: 'eq', value: companyId },
        ...(term.trim()
          ? [{ field: 'name', operation: 'cn', value: term.trim() }]
          : [])
      ],
      pageNum: 0,
      pageSize: 50,
      sortField: 'name',
      direction: 'ASC'
    },
    { signal }
  );
}
export function getMaintenanceAsset(id: number, signal?: AbortSignal) {
  return api.get<MaintenanceAssetOption>(`assets/${id}`, { signal });
}
export function initialAssetId(search: string): number | null {
  const value = new URLSearchParams(search).get('assetId');
  if (!value || !/^\d+$/.test(value)) return null;
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}
