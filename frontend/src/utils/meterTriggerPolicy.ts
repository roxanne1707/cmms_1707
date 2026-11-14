import * as Yup from 'yup';
import { IField } from '../content/own/type';
import WorkOrderMeterTrigger from '../models/owns/workOrderMeterTrigger';

export const meterPolicyLabel = (t, policy: string) =>
  t(policy === 'ONCE' ? 'meter_policy_once' : 'meter_policy_episode');

export const meterPolicyValues = (t, trigger?: Partial<WorkOrderMeterTrigger>) => ({
  episodePolicy: {
    value: trigger?.episodePolicy ?? 'PER_EPISODE',
    label: meterPolicyLabel(t, trigger?.episodePolicy ?? 'PER_EPISODE')
  },
  waitBefore: trigger?.waitBefore ?? 0
});

export const meterPolicyFields = (t): IField[] => [
  {
    name: 'episodePolicy', type: 'select', label: t('meter_policy'), required: true,
    items: ['PER_EPISODE', 'ONCE'].map(value => ({ value, label: meterPolicyLabel(t, value) })),
    helperText: t('meter_policy_help')
  },
  {
    name: 'waitBefore', type: 'number', min: 0, max: 2147483647, step: 1, label: t('meter_wait_seconds'), required: true,
    helperText: t('meter_wait_help')
  }
];

export const meterPolicyValidation = (t) => ({
  episodePolicy: Yup.object({ value: Yup.string().oneOf(['PER_EPISODE', 'ONCE']).required(t('required_field')) })
    .nullable().required(t('required_field')),
  waitBefore: Yup.number().typeError(t('meter_wait_invalid')).integer(t('meter_wait_invalid'))
    .min(0, t('meter_wait_invalid')).max(2147483647, t('meter_wait_invalid')).required(t('meter_wait_invalid')),
  value: Yup.number().typeError(t('meter_threshold_invalid')).integer(t('meter_threshold_invalid'))
    .min(-2147483648, t('meter_threshold_invalid')).max(2147483647, t('meter_threshold_invalid')).required(t('required_value'))
});

export const meterPolicyPayload = (values) => ({
  episodePolicy: values.episodePolicy.value,
  waitBefore: Number(values.waitBefore)
});
