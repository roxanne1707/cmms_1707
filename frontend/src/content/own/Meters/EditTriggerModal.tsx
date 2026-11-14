import { getErrorMessage } from '../../../utils/api';
import { meterPolicyFields, meterPolicyValues, meterPolicyValidation, meterPolicyPayload } from '../../../utils/meterTriggerPolicy';
import { Dialog, DialogContent, DialogTitle, Typography } from '@mui/material';
import { useTranslation } from 'react-i18next';
import Form from '../components/form';
import * as Yup from 'yup';
import { IField } from '../type';
import { formatSelect, formatSelectMultiple, formatCustomFields } from '../../../utils/formatters';
import { useDispatch, useSelector } from '../../../store';
import { editWorkOrderMeterTrigger } from '../../../slices/workOrderMeterTrigger';
import { getWOBaseFields, getWOBaseValues } from '../../../utils/woBase';
import { getCustomFields } from '../../../slices/customField';
import { CustomFieldEntityType } from '../../../models/owns/customField';
import { getCustomFieldsRequiredShape } from '../../own/type';
import Meter from '../../../models/owns/meter';
import WorkOrderMeterTrigger from '../../../models/owns/workOrderMeterTrigger';
import { useContext, useEffect } from 'react';
import { CompanySettingsContext } from '../../../contexts/CompanySettingsContext';
import { CustomSnackBarContext } from '../../../contexts/CustomSnackBarContext';
import { handleFileUpload } from '../../../utils/overall';

interface EditTriggerProps {
  open: boolean;
  onClose: () => void;
  meter: Meter;
  workOrderMeterTrigger: WorkOrderMeterTrigger;
}
export default function EditTriggerModal({
  open,
  onClose,
  meter,
  workOrderMeterTrigger
}: EditTriggerProps) {
  const { t }: { t: any } = useTranslation();
  const dispatch = useDispatch();
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const { uploadFiles } = useContext(CompanySettingsContext);
  const { customFields } = useSelector((state) => state.customFields);

  useEffect(() => {
    if (open && !customFields.length) {
      dispatch(getCustomFields());
    }
  }, [open]);

  const fields: Array<IField> = [
    {
      name: 'name',
      type: 'text',
      label: t('trigger_name'),
      required: true
    },
    {
      name: 'triggerCondition',
      type: 'select',
      label: t('when_reading_is'),
      items: [
        { label: t('greater_than'), value: 'MORE_THAN' },
        { label: t('lower_than'), value: 'LESS_THAN' }
      ],
      midWidth: true,
      required: true
    },
    {
      name: 'value',
      type: 'number',
      min: -2147483648,
      max: 2147483647,
      step: 1,
      label: t('value') + '(' + meter.unit + ')',
      midWidth: true,
      required: true
    },
    ...meterPolicyFields(t),
    {
      name: 'workOrderConfig',
      type: 'titleGroupField',
      label: t('wo_configuration')
    },
    ...getWOBaseFields(t, customFields)
  ];
  const onEditSuccess = () => {
    onClose();
    showSnackBar(t('wo_trigger_edit_success'), 'success');
  };
  const onEditFailure = (err) =>
    showSnackBar(getErrorMessage(err, t('wo_trigger_edit_failure')), 'error');

  const shape = {
    name: Yup.string().required(t('required_trigger_name')),
    title: Yup.string().required(t('required_wo_title')),
    ...meterPolicyValidation(t),
    triggerCondition: Yup.object().required(t('required_trigger_condition')),
    ...getCustomFieldsRequiredShape(
      customFields,
      CustomFieldEntityType.WORK_ORDER,
      t
    )
  };
  const formatValues = (values) => {
    const newValues = { ...values, ...meterPolicyPayload(values) };
    newValues.primaryUser = formatSelect(newValues.primaryUser);
    newValues.location = formatSelect(newValues.location);
    newValues.team = formatSelect(newValues.team);
    newValues.category = formatSelect(newValues.category);
    newValues.asset = formatSelect(newValues.asset);
    newValues.assignedTo = formatSelectMultiple(newValues.assignedTo);
    newValues.priority = newValues.priority?.value;
    newValues.triggerCondition = newValues.triggerCondition.value;
    return formatCustomFields(newValues);
  };
  return (
    <Dialog fullWidth maxWidth="sm" open={open} onClose={onClose}>
      <DialogTitle
        sx={{
          p: 3
        }}
      >
        <Typography variant="h4" gutterBottom>
          {t('edit_wo_trigger')}
        </Typography>
        <Typography variant="subtitle2">
          {t('edit_wo_trigger_description')}
        </Typography>
      </DialogTitle>
      <DialogContent
        dividers
        sx={{
          p: 3
        }}
      >
        <Typography variant="body2" sx={{ mb: 2 }} role="note">
          {t('meter_policy_help')} {t('meter_wait_help')}
        </Typography>
        <Form
          fields={fields}
          validation={Yup.object().shape(shape)}
          submitText={t('save')}
          values={{
            ...workOrderMeterTrigger,
            triggerCondition: {
              label: t(workOrderMeterTrigger?.triggerCondition),
              value: workOrderMeterTrigger?.triggerCondition
            },
            ...getWOBaseValues(t, workOrderMeterTrigger),
            ...meterPolicyValues(t, workOrderMeterTrigger)
          }}
          onChange={({ field, e }) => {}}
          onSubmit={async (values) => {
            let formattedValues = formatValues(values);
            try {
              const imageAndFiles = await handleFileUpload(
                {
                  files: formattedValues.files,
                  image: formattedValues.image
                },
                uploadFiles
              );

              formattedValues = {
                ...formattedValues,
                image: imageAndFiles.image,
                files: imageAndFiles.files
              };

              await dispatch(
                editWorkOrderMeterTrigger(
                  meter.id,
                  workOrderMeterTrigger.id,
                  formattedValues
                )
              );
              onEditSuccess();
            } catch (err) {
              onEditFailure(err);

            }
          }}
        />
      </DialogContent>
    </Dialog>
  );
}
