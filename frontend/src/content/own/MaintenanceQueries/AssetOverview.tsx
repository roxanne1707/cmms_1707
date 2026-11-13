import { useState } from 'react';
import {
  Alert,
  Box,
  Card,
  CardContent,
  Grid,
  Stack,
  Typography
} from '@mui/material';
import { useLocation } from 'react-router-dom';
import useAuth from '../../../hooks/useAuth';
import {
  AssetWorkOrderOverview,
  MaintenancePeriod
} from '../../../models/owns/maintenanceQuery';
import {
  canViewMaintenanceQuery,
  defaultMaintenancePeriod,
  initialAssetId,
  maintenancePeriodError,
  maintenancePeriodPayload,
  maintenanceScopeKey,
  queryAssetOverview
} from '../../../utils/maintenanceQueries';
import {
  AssetPicker,
  cycleHours,
  PeriodFields,
  QueryActions,
  QueryFeedback,
  QueryPage,
  useMaintenanceText,
  useQueryResource,
  utcLabel
} from './shared';

export default function AssetOverview() {
  const { user, company } = useAuth();
  const location = useLocation();
  const mt = useMaintenanceText();
  if (!canViewMaintenanceQuery(user, company))
    return (
      <QueryPage
        title={mt('maintenance_overview_title')}
        description={mt('maintenance_overview_description')}
      >
        <Alert severity="warning">{mt('maintenance_access_denied')}</Alert>
      </QueryPage>
    );
  return (
    <OverviewContent
      key={`${maintenanceScopeKey(user, company)}:${location.search}`}
      companyId={company.id}
      initialAsset={initialAssetId(location.search)}
    />
  );
}

function OverviewContent({
  companyId,
  initialAsset
}: {
  companyId: number;
  initialAsset: number | null;
}) {
  const mt = useMaintenanceText();
  const [draft, setDraft] = useState<MaintenancePeriod>(() =>
    defaultMaintenancePeriod()
  );
  const [assetId, setAssetId] = useState<number | null>(initialAsset);
  const [applied, setApplied] = useState<{
    assetId: number;
    period: MaintenancePeriod;
  }>(() => (initialAsset ? { assetId: initialAsset, period: draft } : null));
  const [revision, setRevision] = useState(0);
  const [assetPending, setAssetPending] = useState(false);
  const [validation, setValidation] = useState<string>(null);
  const key = applied ? JSON.stringify([companyId, applied, revision]) : null;
  const result = useQueryResource<AssetWorkOrderOverview>(key, (signal) =>
    queryAssetOverview(
      applied.assetId,
      companyId,
      maintenancePeriodPayload(applied.period),
      signal
    )
  );
  const dirty =
    !!applied &&
    JSON.stringify({ assetId, period: draft }) !== JSON.stringify(applied);
  const data = !dirty && !assetPending ? result.data : undefined;
  const submit = (event) => {
    event.preventDefault();
    if (assetPending) {
      setValidation('maintenance_asset_pending');
      return;
    }
    const error =
      maintenancePeriodError(draft) ||
      (!assetId ? 'maintenance_select_asset' : null);
    setValidation(error);
    if (!error) {
      setApplied({ assetId, period: { ...draft } });
      setRevision((v) => v + 1);
    }
  };
  const metrics = data
    ? [
        ['maintenance_total_orders', data.total.toLocaleString()],
        ['maintenance_completed_orders', data.complete.toLocaleString()],
        [
          'maintenance_completion_rate',
          data.completionRate == null
            ? '—'
            : `${(Number(data.completionRate) * 100).toFixed(1)}%`
        ],
        ['maintenance_cycle_hours', cycleHours(data.averageCycleSeconds)]
      ]
    : [];
  return (
    <QueryPage
      title={mt('maintenance_overview_title')}
      description={mt('maintenance_overview_description')}
    >
      <Card>
        <CardContent>
          <Box component="form" onSubmit={submit} noValidate>
            <Grid container spacing={2}>
              <Grid item xs={12}>
                <AssetPicker
                  value={assetId}
                  onChange={setAssetId}
                  companyId={companyId}
                  onPendingChange={setAssetPending}
                  required
                />
              </Grid>
              <PeriodFields value={draft} onChange={setDraft} />
              <Grid item xs={12}>
                {validation && (
                  <Alert severity="error" sx={{ mb: 2 }}>
                    {mt(validation)}
                  </Alert>
                )}
                <QueryActions
                  loading={result.loading}
                  dirty={dirty}
                  blocked={assetPending}
                  hasRequest={!!applied}
                  onRefresh={() => setRevision((v) => v + 1)}
                />
              </Grid>
            </Grid>
          </Box>
        </CardContent>
      </Card>
      <Alert severity="info">{mt('maintenance_metric_hint')}</Alert>
      {!applied && (
        <Alert severity="info">{mt('maintenance_overview_choose')}</Alert>
      )}
      {!dirty && !assetPending && (
        <QueryFeedback loading={result.loading} error={result.error} />
      )}
      {data && (
        <>
          {data.total === 0 && (
            <Alert severity="info">{mt('maintenance_empty_overview')}</Alert>
          )}
          <Grid
            container
            spacing={2}
            sx={{
              width: 'calc(100% + 16px) !important',
              ml: '-16px !important'
            }}
          >
            {metrics.map(([label, value]) => (
              <Grid item xs={12} sm={6} lg={3} key={label}>
                <Card sx={{ height: '100%' }}>
                  <CardContent>
                    <Typography color="text.secondary" gutterBottom>
                      {mt(label)}
                    </Typography>
                    <Typography variant="h2" component="p" data-testid={label}>
                      {value}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
            ))}
          </Grid>
          <Card>
            <CardContent>
              <Stack spacing={1}>
                <Typography>
                  {mt('maintenance_samples', {
                    valid: data.validCycleSamples,
                    invalid: data.invalidCycleSamples
                  })}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {mt('maintenance_applied_period', {
                    start: utcLabel(data.start),
                    end: utcLabel(data.end)
                  })}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {mt('maintenance_generated', {
                    time: utcLabel(data.generatedAt)
                  })}
                </Typography>
              </Stack>
            </CardContent>
          </Card>
        </>
      )}
    </QueryPage>
  );
}
