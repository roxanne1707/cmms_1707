export type MaintenanceStatus = 'OPEN' | 'IN_PROGRESS' | 'ON_HOLD' | 'COMPLETE';
export type MaintenancePriority = 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH';
export interface MaintenancePeriod {
  start: string;
  end: string;
  includeArchived: boolean;
  includeDemo: boolean;
}
export interface AssetWorkOrderOverview extends MaintenancePeriod {
  metricVersion: string;
  companyId: number;
  assetId: number;
  epoch: number;
  generatedAt: string;
  total: number;
  complete: number;
  completionRate: number | null;
  averageCycleSeconds: number | null;
  validCycleSamples: number;
  invalidCycleSamples: number;
}
export interface MaintenanceLedgerQuery extends MaintenancePeriod {
  assetId?: number;
  status?: MaintenanceStatus;
  priority?: MaintenancePriority;
  meterTriggered?: boolean;
  page: number;
  size: number;
  sort: 'CREATED_DESC' | 'CREATED_ASC';
}
export interface MaintenanceLedgerRow {
  id: number;
  customId: string;
  title: string;
  assetId: number | null;
  assetName: string | null;
  status: MaintenanceStatus;
  priority: MaintenancePriority;
  createdAt: string;
  completedOn: string | null;
  archived: boolean;
  demo: boolean;
  cycleSeconds: number | null;
  meterTriggered: boolean;
}
export interface MaintenanceLedgerPage {
  content: MaintenanceLedgerRow[];
  total: number;
  page: number;
  size: number;
}
export interface MaintenanceAssetOption {
  id: number;
  name: string;
  customId?: string;
}
