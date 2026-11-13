import { useState } from 'react';
import {
  Alert,
  Box,
  Card,
  CardContent,
  Chip,
  Grid,
  Link,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Typography
} from '@mui/material';
import { Link as RouterLink, useLocation } from 'react-router-dom';
import useAuth from '../../../hooks/useAuth';
import {
  MaintenanceLedgerPage,
  MaintenancePeriod,
  MaintenancePriority,
  MaintenanceStatus
} from '../../../models/owns/maintenanceQuery';
import {
  canViewMaintenanceQuery,
  defaultMaintenancePeriod,
  initialAssetId,
  maintenancePeriodError,
  maintenancePeriodPayload,
  maintenanceScopeKey,
  queryMaintenanceLedger
} from '../../../utils/maintenanceQueries';
import { getAssetUrl, getWorkOrderUrl } from '../../../utils/urlPaths';
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

type Filters = MaintenancePeriod & {
  assetId: number | null;
  status: MaintenanceStatus | '';
  priority: MaintenancePriority | '';
  source: '' | 'meter' | 'other';
  sort: 'CREATED_DESC' | 'CREATED_ASC';
};
const statuses: MaintenanceStatus[] = [
  'OPEN',
  'IN_PROGRESS',
  'ON_HOLD',
  'COMPLETE'
];
const priorities: MaintenancePriority[] = ['NONE', 'LOW', 'MEDIUM', 'HIGH'];

export default function MaintenanceLedger() {
  const { user, company } = useAuth();
  const location = useLocation();
  const mt = useMaintenanceText();
  if (!canViewMaintenanceQuery(user, company, true))
    return (
      <QueryPage
        title={mt('maintenance_ledger_title')}
        description={mt('maintenance_ledger_description')}
      >
        <Alert severity="warning">{mt('maintenance_access_denied')}</Alert>
      </QueryPage>
    );
  return (
    <LedgerContent
      key={`${maintenanceScopeKey(user, company)}:${location.search}`}
      companyId={company.id}
      initialAsset={initialAssetId(location.search)}
    />
  );
}

function LedgerContent({
  companyId,
  initialAsset
}: {
  companyId: number;
  initialAsset: number | null;
}) {
  const mt = useMaintenanceText();
  const [draft, setDraft] = useState<Filters>(() => ({
    ...defaultMaintenancePeriod(),
    assetId: initialAsset,
    status: '',
    priority: '',
    source: '',
    sort: 'CREATED_DESC'
  }));
  const [applied, setApplied] = useState<Filters>(draft);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [revision, setRevision] = useState(0);
  const [assetPending, setAssetPending] = useState(false);
  const [validation, setValidation] = useState<string>(null);
  const key = JSON.stringify([companyId, applied, page, size, revision]);
  const result = useQueryResource<MaintenanceLedgerPage>(key, (signal) =>
    queryMaintenanceLedger(
      companyId,
      {
        ...maintenancePeriodPayload(applied),
        assetId: applied.assetId || undefined,
        status: applied.status || undefined,
        priority: applied.priority || undefined,
        meterTriggered:
          applied.source === '' ? undefined : applied.source === 'meter',
        page,
        size,
        sort: applied.sort
      },
      signal
    )
  );
  const dirty = JSON.stringify(applied) !== JSON.stringify(draft);
  const data = !dirty && !assetPending ? result.data : undefined;
  const submit = (event) => {
    event.preventDefault();
    if (assetPending) {
      setValidation('maintenance_asset_pending');
      return;
    }
    const error = maintenancePeriodError(draft);
    setValidation(error);
    if (!error) {
      setApplied({ ...draft });
      setPage(0);
      setRevision((v) => v + 1);
    }
  };
  return (
    <QueryPage
      title={mt('maintenance_ledger_title')}
      description={mt('maintenance_ledger_description')}
    >
      <Card>
        <CardContent>
          <Box component="form" onSubmit={submit} noValidate>
            <Grid container spacing={2}>
              <Grid item xs={12}>
                <AssetPicker
                  value={draft.assetId}
                  onChange={(assetId) =>
                    setDraft((current) => ({ ...current, assetId }))
                  }
                  companyId={companyId}
                  onPendingChange={setAssetPending}
                />
              </Grid>
              <PeriodFields
                value={draft}
                onChange={(period) =>
                  setDraft((current) => ({ ...current, ...period }))
                }
              />
              <Grid item xs={12} sm={6} md={3}>
                <TextField
                  fullWidth
                  select
                  label={mt('maintenance_status')}
                  value={draft.status}
                  onChange={(e) =>
                    setDraft((current) => ({
                      ...current,
                      status: e.target.value as Filters['status']
                    }))
                  }
                >
                  <MenuItem value="">{mt('maintenance_all')}</MenuItem>
                  {statuses.map((value) => (
                    <MenuItem key={value} value={value}>
                      {mt(`maintenance_status_${value}`)}
                    </MenuItem>
                  ))}
                </TextField>
              </Grid>
              <Grid item xs={12} sm={6} md={3}>
                <TextField
                  fullWidth
                  select
                  label={mt('maintenance_priority')}
                  value={draft.priority}
                  onChange={(e) =>
                    setDraft((current) => ({
                      ...current,
                      priority: e.target.value as Filters['priority']
                    }))
                  }
                >
                  <MenuItem value="">{mt('maintenance_all')}</MenuItem>
                  {priorities.map((value) => (
                    <MenuItem key={value} value={value}>
                      {mt(`maintenance_priority_${value}`)}
                    </MenuItem>
                  ))}
                </TextField>
              </Grid>
              <Grid item xs={12} sm={6} md={3}>
                <TextField
                  fullWidth
                  select
                  label={mt('maintenance_source')}
                  value={draft.source}
                  onChange={(e) =>
                    setDraft((current) => ({
                      ...current,
                      source: e.target.value as Filters['source']
                    }))
                  }
                >
                  <MenuItem value="">{mt('maintenance_all')}</MenuItem>
                  <MenuItem value="meter">
                    {mt('maintenance_meter_source')}
                  </MenuItem>
                  <MenuItem value="other">
                    {mt('maintenance_other_source')}
                  </MenuItem>
                </TextField>
              </Grid>
              <Grid item xs={12} sm={6} md={3}>
                <TextField
                  fullWidth
                  select
                  label={mt('maintenance_sort')}
                  value={draft.sort}
                  onChange={(e) =>
                    setDraft((current) => ({
                      ...current,
                      sort: e.target.value as Filters['sort']
                    }))
                  }
                >
                  <MenuItem value="CREATED_DESC">
                    {mt('maintenance_newest')}
                  </MenuItem>
                  <MenuItem value="CREATED_ASC">
                    {mt('maintenance_oldest')}
                  </MenuItem>
                </TextField>
              </Grid>
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
                  hasRequest
                  onRefresh={() => setRevision((v) => v + 1)}
                />
              </Grid>
            </Grid>
          </Box>
        </CardContent>
      </Card>
      <Alert severity="info">{mt('maintenance_ledger_metric_hint')}</Alert>
      {!dirty && !assetPending && (
        <QueryFeedback loading={result.loading} error={result.error} />
      )}
      {data && (
        <Card>
          <Box sx={{ p: 2 }}>
            <Typography variant="h4" component="h2">
              {mt('maintenance_results', { count: data.total })}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {mt('maintenance_table_utc')}
            </Typography>
          </Box>
          {data.content.length === 0 ? (
            <Alert severity="info" sx={{ mx: 2, mb: 2 }}>
              {mt(
                page > 0 ? 'maintenance_empty_page' : 'maintenance_empty_ledger'
              )}
            </Alert>
          ) : (
            <TableContainer>
              <Table
                size="small"
                aria-label={mt('maintenance_ledger_title')}
                sx={{ minWidth: 920 }}
              >
                <TableHead>
                  <TableRow>
                    {[
                      'maintenance_work_order',
                      'maintenance_asset',
                      'maintenance_status',
                      'maintenance_priority',
                      'maintenance_created',
                      'maintenance_completed',
                      'maintenance_cycle_hours',
                      'maintenance_source'
                    ].map((label) => (
                      <TableCell key={label}>{mt(label)}</TableCell>
                    ))}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {data.content.map((row) => (
                    <TableRow key={row.id} hover>
                      <TableCell>
                        <Link
                          component={RouterLink}
                          to={getWorkOrderUrl(row.id)}
                        >
                          {row.customId || `#${row.id}`} · {row.title}
                        </Link>
                        <Stack direction="row" spacing={0.5} mt={0.5}>
                          {row.archived && (
                            <Chip
                              size="small"
                              label={mt('maintenance_archived')}
                            />
                          )}
                          {row.demo && (
                            <Chip size="small" label={mt('maintenance_demo')} />
                          )}
                        </Stack>
                      </TableCell>
                      <TableCell>
                        {row.assetId ? (
                          <Link
                            component={RouterLink}
                            to={getAssetUrl(row.assetId)}
                          >
                            {row.assetName || `#${row.assetId}`}
                          </Link>
                        ) : (
                          mt('maintenance_no_asset')
                        )}
                      </TableCell>
                      <TableCell>
                        <Chip
                          size="small"
                          color={
                            row.status === 'COMPLETE'
                              ? 'success'
                              : row.status === 'IN_PROGRESS'
                              ? 'primary'
                              : row.status === 'ON_HOLD'
                              ? 'warning'
                              : 'default'
                          }
                          label={mt(`maintenance_status_${row.status}`)}
                        />
                      </TableCell>
                      <TableCell>
                        {mt(`maintenance_priority_${row.priority}`)}
                      </TableCell>
                      <TableCell sx={{ whiteSpace: 'nowrap' }}>
                        {utcLabel(row.createdAt)}
                      </TableCell>
                      <TableCell sx={{ whiteSpace: 'nowrap' }}>
                        {utcLabel(row.completedOn)}
                      </TableCell>
                      <TableCell>{cycleHours(row.cycleSeconds)}</TableCell>
                      <TableCell>
                        {mt(
                          row.meterTriggered
                            ? 'maintenance_meter_source'
                            : 'maintenance_other_source'
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
          <TablePagination
            component="div"
            count={data.total}
            page={page}
            rowsPerPage={size}
            rowsPerPageOptions={[10, 25, 50, 100]}
            labelRowsPerPage={mt('maintenance_rows_per_page')}
            labelDisplayedRows={({ from, to, count }) =>
              mt('maintenance_page_range', { from, to, count })
            }
            getItemAriaLabel={(type) =>
              mt(
                type === 'next'
                  ? 'maintenance_next_page'
                  : 'maintenance_previous_page'
              )
            }
            nextIconButtonProps={{
              disabled: page >= 10000 || (page + 1) * size >= data.total
            }}
            onPageChange={(_event, next) =>
              setPage(Math.max(0, Math.min(next, 10000)))
            }
            onRowsPerPageChange={(event) => {
              setSize(Number(event.target.value));
              setPage(0);
            }}
            sx={{
              '.MuiTablePagination-toolbar': {
                flexWrap: 'wrap',
                justifyContent: 'flex-end',
                px: 1
              },
              '.MuiTablePagination-spacer': {
                display: { xs: 'none', sm: 'block' }
              }
            }}
          />
          {page >= 10000 && (
            <Alert severity="info">{mt('maintenance_page_limit')}</Alert>
          )}
        </Card>
      )}
    </QueryPage>
  );
}
