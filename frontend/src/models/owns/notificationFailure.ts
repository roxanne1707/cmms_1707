export interface FailedMaintenanceEvent {
  event_id: string;
  handler: string;
  replay_generation: number;
  business_attempt: number;
  last_error: string | null;
  updated_at: string | number;
}

export interface FailedMaintenanceDelivery {
  id: string;
  event_id: string;
  channel: string;
  purpose: string;
  state: 'DEAD' | 'SKIPPED';
  attempt: number;
  last_error: string | null;
  created_at: string | number;
}

export interface MaintenanceFailures {
  events: FailedMaintenanceEvent[];
  deliveries: FailedMaintenanceDelivery[];
}

export interface MaintenanceReplayResult {
  success: boolean;
  message: string;
}

export type MaintenanceRecoveryTarget = {
  type: 'event' | 'delivery';
  id: string;
};
