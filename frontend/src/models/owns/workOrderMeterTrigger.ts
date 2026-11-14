import { WorkOrderBase } from './workOrderBase';

export default interface WorkOrderMeterTrigger extends WorkOrderBase {
  /** Legacy transport field; episodePolicy determines creation behavior. */
  recurrent: boolean;
  episodePolicy?: 'PER_EPISODE' | 'ONCE';
  name: string;
  triggerCondition: 'LESS_THAN' | 'MORE_THAN';
  value: number;
  waitBefore: number;
}
