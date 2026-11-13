import { useCallback, useContext, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Helmet } from 'react-helmet-async';
import {
  Alert,
  Box,
  Button,
  Card,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Grid,
  Stack,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  Tabs,
  TextField,
  Typography
} from '@mui/material';
import RefreshTwoToneIcon from '@mui/icons-material/RefreshTwoTone';
import ReplayTwoToneIcon from '@mui/icons-material/ReplayTwoTone';
import useAuth from '../../../hooks/useAuth';
import { TitleContext } from '../../../contexts/TitleContext';
import {
  MaintenanceFailures,
  MaintenanceRecoveryTarget
} from '../../../models/owns/notificationFailure';
import {
  getNotificationFailures,
  isValidRecoveryReason,
  notificationFailureErrorKey,
  recoverNotification
} from '../../../utils/notificationFailures';

type Feedback = { severity: 'success' | 'warning' | 'error'; key: string };

function formatUtc(value: string | number, language: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat(language.replace(/_/g, '-'), {
    dateStyle: 'short',
    timeStyle: 'medium',
    timeZone: 'UTC'
  }).format(date);
}

function FailureWorkspace() {
  const { t, i18n }: { t: any; i18n: any } = useTranslation();
  const [data, setData] = useState<MaintenanceFailures | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<Feedback | null>(null);
  const [tab, setTab] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [target, setTarget] = useState<MaintenanceRecoveryTarget | null>(null);
  const [reason, setReason] = useState('');
  const [reasonTouched, setReasonTouched] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const mounted = useRef(false);
  const submitLock = useRef(false);
  const loadGeneration = useRef(0);
  const loadController = useRef<AbortController | null>(null);

  const load = useCallback(async () => {
    const generation = ++loadGeneration.current;
    loadController.current?.abort();
    const controller = new AbortController();
    loadController.current = controller;
    setLoading(true);
    setLoadError(null);
    // A failed authorization must not leave a previously fetched company's rows visible.
    setData(null);
    try {
      const result = await getNotificationFailures(controller.signal);
      if (!mounted.current || generation !== loadGeneration.current) return;
      setData(result);
      setPage(0);
    } catch (error) {
      if (
        !mounted.current ||
        generation !== loadGeneration.current ||
        controller.signal.aborted
      )
        return;
      setLoadError(notificationFailureErrorKey(error));
    } finally {
      if (mounted.current && generation === loadGeneration.current)
        setLoading(false);
    }
  }, []);

  useEffect(() => {
    mounted.current = true;
    void load();
    return () => {
      mounted.current = false;
      loadGeneration.current += 1;
      loadController.current?.abort();
    };
  }, [load]);

  const beginRecovery = (next: MaintenanceRecoveryTarget) => {
    if (submitLock.current || loading) return;
    setTarget(next);
    setReason('');
    setReasonTouched(false);
    setFeedback(null);
  };

  const submitRecovery = async () => {
    setReasonTouched(true);
    if (!target || !isValidRecoveryReason(reason) || submitLock.current) return;
    submitLock.current = true;
    setSubmitting(true);
    try {
      const result = await recoverNotification(target, reason);
      if (!result.success) throw new Error('Recovery not confirmed');
      if (!mounted.current) return;
      setTarget(null);
      setReason('');
      setFeedback({
        severity: 'success',
        key: 'maintenance_failures_scheduled'
      });
      await load();
    } catch (error) {
      if (!mounted.current) return;
      const status = (error as { status?: number })?.status;
      setTarget(null);
      setReason('');
      setFeedback({
        severity: status === 403 || status === 401 ? 'error' : 'warning',
        key: notificationFailureErrorKey(error, true)
      });
      // In particular, a network error does not prove that POST failed on the server.
      // Refresh before another explicit user action; never automatically repeat POST.
      await load();
    } finally {
      submitLock.current = false;
      if (mounted.current) setSubmitting(false);
    }
  };

  const events = data?.events ?? [];
  const deliveries = data?.deliveries ?? [];
  const count = tab === 0 ? events.length : deliveries.length;
  const offset = page * pageSize;
  const reasonValid = isValidRecoveryReason(reason);
  const descriptionId = 'notification-recovery-description';

  return (
    <>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        justifyContent="space-between"
        alignItems={{ xs: 'stretch', sm: 'center' }}
        spacing={2}
        mb={3}
      >
        <Box>
          <Typography variant="h3" component="h1">
            {t('maintenance_failures_title')}
          </Typography>
          <Typography color="text.secondary" mt={1}>
            {t('maintenance_failures_description')}
          </Typography>
        </Box>
        <Button
          variant="outlined"
          startIcon={<RefreshTwoToneIcon />}
          onClick={() => {
            setFeedback(null);
            void load();
          }}
          disabled={loading || submitting}
          sx={{ flexShrink: 0 }}
        >
          {t('maintenance_failures_refresh')}
        </Button>
      </Stack>

      <Alert severity="info" sx={{ mb: 2 }}>
        {t('maintenance_failures_scope')}
      </Alert>
      {feedback && (
        <Alert severity={feedback.severity} sx={{ mb: 2 }}>
          {t(feedback.key)}
        </Alert>
      )}
      {loadError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {t(loadError)}
        </Alert>
      )}

      {loading ? (
        <Card sx={{ p: 5 }}>
          <Stack
            direction="row"
            alignItems="center"
            justifyContent="center"
            spacing={2}
            role="status"
          >
            <CircularProgress size={24} />
            <Typography>{t('maintenance_failures_loading')}</Typography>
          </Stack>
        </Card>
      ) : data ? (
        <>
          <Grid container spacing={2} mb={2}>
            {[
              ['maintenance_failures_events_count', events.length],
              [
                'maintenance_failures_dead_count',
                deliveries.filter((row) => row.state === 'DEAD').length
              ],
              [
                'maintenance_failures_skipped_count',
                deliveries.filter((row) => row.state === 'SKIPPED').length
              ]
            ].map(([label, value]) => (
              <Grid item xs={12} sm={4} key={label}>
                <Card sx={{ p: 2 }}>
                  <Typography color="text.secondary">{t(label)}</Typography>
                  <Typography variant="h3" component="p" mt={1}>
                    {value}
                  </Typography>
                </Card>
              </Grid>
            ))}
          </Grid>
          <Card>
            <Tabs
              value={tab}
              onChange={(_, value) => {
                setTab(value);
                setPage(0);
              }}
              variant="scrollable"
              scrollButtons="auto"
              aria-label={t('maintenance_failures_title')}
            >
              <Tab
                id="failure-events-tab"
                aria-controls="failure-events-panel"
                label={t('maintenance_failures_events')}
              />
              <Tab
                id="failure-deliveries-tab"
                aria-controls="failure-deliveries-panel"
                label={t('maintenance_failures_deliveries')}
              />
            </Tabs>
            <Box
              role="tabpanel"
              id={
                tab === 0 ? 'failure-events-panel' : 'failure-deliveries-panel'
              }
              aria-labelledby={
                tab === 0 ? 'failure-events-tab' : 'failure-deliveries-tab'
              }
            >
              <Typography color="text.secondary" variant="body2" sx={{ p: 2 }}>
                {t(
                  tab === 0
                    ? 'maintenance_failures_event_help'
                    : 'maintenance_failures_skipped_help'
                )}
              </Typography>
              {count === 0 ? (
                <Box p={4} role="status">
                  <Typography>
                    {t(
                      tab === 0
                        ? 'maintenance_failures_event_empty'
                        : 'maintenance_failures_delivery_empty'
                    )}
                  </Typography>
                </Box>
              ) : (
                <TableContainer>
                  <Table
                    aria-label={t(
                      tab === 0
                        ? 'maintenance_failures_events'
                        : 'maintenance_failures_deliveries'
                    )}
                    sx={{ minWidth: 850, '& td': { verticalAlign: 'top' } }}
                  >
                    <TableHead>
                      <TableRow>
                        <TableCell>
                          {t(
                            tab === 0
                              ? 'maintenance_failures_event_id'
                              : 'maintenance_failures_delivery_id'
                          )}
                        </TableCell>
                        <TableCell>
                          {t(
                            tab === 0
                              ? 'maintenance_failures_handler'
                              : 'maintenance_failures_channel'
                          )}
                        </TableCell>
                        <TableCell>
                          {t(
                            tab === 0
                              ? 'maintenance_failures_attempt'
                              : 'maintenance_failures_state'
                          )}
                        </TableCell>
                        <TableCell>
                          {t('maintenance_failures_last_error')}
                        </TableCell>
                        <TableCell>
                          {t(
                            tab === 0
                              ? 'maintenance_failures_updated'
                              : 'maintenance_failures_created'
                          )}
                        </TableCell>
                        <TableCell>
                          {t('maintenance_failures_actions')}
                        </TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {tab === 0
                        ? events
                            .slice(offset, offset + pageSize)
                            .map((event) => (
                              <TableRow
                                key={`${event.event_id}:${event.replay_generation}:${event.business_attempt}`}
                              >
                                <TableCell
                                  sx={{
                                    maxWidth: 190,
                                    overflowWrap: 'anywhere'
                                  }}
                                >
                                  <Typography variant="body2" component="code">
                                    {event.event_id}
                                  </Typography>
                                </TableCell>
                                <TableCell
                                  sx={{
                                    maxWidth: 140,
                                    overflowWrap: 'anywhere'
                                  }}
                                >
                                  {event.handler}
                                </TableCell>
                                <TableCell>
                                  {event.business_attempt} /{' '}
                                  {event.replay_generation}
                                </TableCell>
                                <TableCell
                                  sx={{
                                    minWidth: 180,
                                    maxWidth: 300,
                                    overflowWrap: 'anywhere',
                                    whiteSpace: 'pre-wrap'
                                  }}
                                >
                                  {event.last_error ||
                                    t('maintenance_failures_no_reason')}
                                </TableCell>
                                <TableCell>
                                  {formatUtc(
                                    event.updated_at,
                                    i18n.language || 'en'
                                  )}
                                </TableCell>
                                <TableCell>
                                  <Button
                                    size="small"
                                    startIcon={<ReplayTwoToneIcon />}
                                    disabled={submitting}
                                    onClick={() =>
                                      beginRecovery({
                                        type: 'event',
                                        id: event.event_id
                                      })
                                    }
                                  >
                                    {t('maintenance_failures_replay')}
                                  </Button>
                                </TableCell>
                              </TableRow>
                            ))
                        : deliveries
                            .slice(offset, offset + pageSize)
                            .map((delivery) => (
                              <TableRow key={delivery.id}>
                                <TableCell
                                  sx={{
                                    maxWidth: 190,
                                    overflowWrap: 'anywhere'
                                  }}
                                >
                                  <Typography
                                    variant="body2"
                                    component="code"
                                    display="block"
                                  >
                                    {delivery.id}
                                  </Typography>
                                  <Typography
                                    variant="caption"
                                    component="code"
                                    color="text.secondary"
                                    display="block"
                                    mt={1}
                                  >
                                    {delivery.event_id}
                                  </Typography>
                                </TableCell>
                                <TableCell
                                  sx={{
                                    maxWidth: 140,
                                    overflowWrap: 'anywhere'
                                  }}
                                >
                                  <Typography variant="body2">
                                    {delivery.channel}
                                  </Typography>
                                  <Typography
                                    variant="caption"
                                    color="text.secondary"
                                  >
                                    {delivery.purpose}
                                  </Typography>
                                </TableCell>
                                <TableCell>
                                  <Chip
                                    size="small"
                                    color={
                                      delivery.state === 'DEAD'
                                        ? 'error'
                                        : 'default'
                                    }
                                    label={t(
                                      delivery.state === 'DEAD'
                                        ? 'maintenance_failures_dead'
                                        : 'maintenance_failures_skipped'
                                    )}
                                  />
                                  <Typography
                                    variant="caption"
                                    display="block"
                                    mt={1}
                                  >
                                    {delivery.attempt}
                                  </Typography>
                                </TableCell>
                                <TableCell
                                  sx={{
                                    minWidth: 180,
                                    maxWidth: 300,
                                    overflowWrap: 'anywhere',
                                    whiteSpace: 'pre-wrap'
                                  }}
                                >
                                  {delivery.last_error ||
                                    t('maintenance_failures_no_reason')}
                                </TableCell>
                                <TableCell>
                                  {formatUtc(
                                    delivery.created_at,
                                    i18n.language || 'en'
                                  )}
                                </TableCell>
                                <TableCell>
                                  <Button
                                    size="small"
                                    startIcon={<ReplayTwoToneIcon />}
                                    disabled={
                                      submitting || delivery.state !== 'DEAD'
                                    }
                                    onClick={() =>
                                      beginRecovery({
                                        type: 'delivery',
                                        id: delivery.id
                                      })
                                    }
                                  >
                                    {t('maintenance_failures_retry')}
                                  </Button>
                                </TableCell>
                              </TableRow>
                            ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
              <TablePagination
                component="div"
                count={count}
                page={page}
                rowsPerPage={pageSize}
                rowsPerPageOptions={[10, 25, 50]}
                onPageChange={(_, next) => setPage(next)}
                onRowsPerPageChange={(event) => {
                  setPageSize(Number(event.target.value));
                  setPage(0);
                }}
                labelRowsPerPage={t('maintenance_failures_rows_per_page')}
                labelDisplayedRows={({ from, to, count: total }) =>
                  total === 0
                    ? t('maintenance_failures_empty_page')
                    : t('maintenance_failures_page_range', {
                        from,
                        to,
                        count: total
                      })
                }
                sx={{ '& .MuiTablePagination-toolbar': { flexWrap: 'wrap' } }}
              />
            </Box>
          </Card>
        </>
      ) : null}

      <Dialog
        open={Boolean(target)}
        onClose={() => {
          if (!submitLock.current) setTarget(null);
        }}
        fullWidth
        maxWidth="sm"
        aria-labelledby="notification-recovery-title"
        aria-describedby={descriptionId}
      >
        <DialogTitle id="notification-recovery-title">
          {t('maintenance_failures_confirm_title')}
        </DialogTitle>
        <DialogContent>
          <DialogContentText id={descriptionId}>
            {t(
              target?.type === 'event'
                ? 'maintenance_failures_event_confirm'
                : 'maintenance_failures_delivery_confirm'
            )}
          </DialogContentText>
          <Typography
            component="code"
            variant="body2"
            sx={{ display: 'block', my: 2, overflowWrap: 'anywhere' }}
          >
            {target?.id}
          </Typography>
          <TextField
            autoFocus
            fullWidth
            multiline
            minRows={3}
            label={t('maintenance_failures_reason')}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            onBlur={() => setReasonTouched(true)}
            disabled={submitting}
            error={reasonTouched && !reasonValid}
            helperText={
              reasonTouched && !reasonValid
                ? t('maintenance_failure_reason_invalid')
                : t('maintenance_failures_reason_hint')
            }
            inputProps={{ maxLength: 500 }}
          />
          <Typography
            variant="caption"
            color="text.secondary"
            display="block"
            textAlign="right"
            mt={1}
          >
            {reason.length} / 500
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setTarget(null)} disabled={submitting}>
            {t('maintenance_failures_cancel')}
          </Button>
          <Button
            variant="contained"
            onClick={() => void submitRecovery()}
            disabled={submitting || !reasonValid}
            startIcon={
              submitting ? (
                <CircularProgress size={16} color="inherit" />
              ) : undefined
            }
          >
            {t(
              submitting
                ? 'maintenance_failures_pending'
                : 'maintenance_failures_confirm'
            )}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}

export default function NotificationFailures() {
  const { user } = useAuth();
  const { t }: { t: any } = useTranslation();
  const { setTitle } = useContext(TitleContext);
  const allowed = Boolean(
    user?.enabled && (user?.ownsCompany || user?.role?.code === 'ADMIN')
  );
  useEffect(() => {
    setTitle(t('maintenance_failures_title'));
  }, [setTitle, t]);

  return (
    <Box sx={{ p: { xs: 2, md: 3 }, maxWidth: 1600, mx: 'auto' }}>
      <Helmet>
        <title>{t('maintenance_failures_title')} | Atlas CMMS</title>
      </Helmet>
      {allowed ? (
        <FailureWorkspace
          key={`${user.id}:${user.companyId}:${user.role?.code}`}
        />
      ) : (
        <Alert severity="warning">
          {t('maintenance_failure_permission_error')}
        </Alert>
      )}
    </Box>
  );
}
