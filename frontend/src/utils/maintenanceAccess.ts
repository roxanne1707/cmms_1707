import type { UserResponseDTO } from '../models/user';
import type { Company } from '../models/owns/company';
import { PermissionEntity } from '../models/owns/role';
import { PlanFeature } from '../models/owns/subscriptionPlan';

export type MaintenancePage = 'overview' | 'ledger' | 'notifications' | 'sla' | 'request-links' | 'sessions';

/** Navigation hint only. Every request remains subject to fresh server authorization. */
export function canSeeMaintenancePage(
  user: UserResponseDTO | null,
  company: Company | null,
  page: MaintenancePage
): boolean {
  if (!user?.enabled || !user.role || !company) return false;
  if (page === 'sessions') return true;
  if (page === 'sla' || page === 'request-links') {
    const view = user.role.viewPermissions || [];
    const others = user.role.viewOtherPermissions || [];
    return (view.includes(PermissionEntity.SETTINGS) || user.role.code === 'LIMITED_ADMIN') &&
      view.includes(PermissionEntity.WORK_ORDERS) && others.includes(PermissionEntity.WORK_ORDERS) &&
      (page !== 'request-links' || (view.includes(PermissionEntity.REQUESTS) && others.includes(PermissionEntity.REQUESTS)));
  }
  if (page === 'notifications') return !!user.ownsCompany || user.role.code === 'ADMIN';
  const view = user.role.viewPermissions || [];
  const others = user.role.viewOtherPermissions || [];
  return (
    company.subscription?.subscriptionPlan?.features?.includes(PlanFeature.ANALYTICS) &&
    [PermissionEntity.ANALYTICS, PermissionEntity.ASSETS, PermissionEntity.WORK_ORDERS].every(p => view.includes(p)) &&
    others.includes(PermissionEntity.WORK_ORDERS) &&
    (page !== 'ledger' || others.includes(PermissionEntity.ASSETS))
  );
}
