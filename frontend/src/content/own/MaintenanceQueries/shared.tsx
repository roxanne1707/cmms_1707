import { ReactNode, useContext, useEffect, useRef, useState } from 'react';
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Card,
  Checkbox,
  CircularProgress,
  FormControlLabel,
  Grid,
  IconButton,
  Stack,
  TextField,
  Typography
} from '@mui/material';
import { Helmet } from 'react-helmet-async';
import ClearIcon from '@mui/icons-material/Clear';
import { useTranslation } from 'react-i18next';
import { TitleContext } from '../../../contexts/TitleContext';
import {
  MaintenanceAssetOption,
  MaintenancePeriod
} from '../../../models/owns/maintenanceQuery';
import {
  getMaintenanceAsset,
  maintenanceErrorKey,
  searchMaintenanceAssets
} from '../../../utils/maintenanceQueries';
import { maintenanceQueryEn, maintenanceQueryZh } from './translations';

export function useMaintenanceText() {
  const { t, i18n } = useTranslation();
  const fallback = i18n.language?.toLowerCase().startsWith('zh')
    ? maintenanceQueryZh
    : maintenanceQueryEn;
  return (key: string, options: Record<string, any> = {}): string =>
    t(key, {
      defaultValue: fallback[key] || maintenanceQueryEn[key] || key,
      ...options
    });
}

export function QueryPage({
  title,
  description,
  children
}: {
  title: string;
  description: string;
  children: ReactNode;
}) {
  const { setTitle } = useContext(TitleContext);
  useEffect(() => {
    setTitle(title);
  }, [setTitle, title]);
  return (
    <Box sx={{ p: { xs: 2, md: 3 }, maxWidth: 1600, mx: 'auto' }}>
      <Helmet>
        <title>{title} | Atlas CMMS</title>
      </Helmet>
      <Typography variant="h3" component="h1" gutterBottom>
        {title}
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        {description}
      </Typography>
      <Stack spacing={2}>{children}</Stack>
    </Box>
  );
}

export function useQueryResource<T>(
  key: string | null,
  load: (signal: AbortSignal) => Promise<T>
) {
  const loader = useRef(load);
  loader.current = load;
  const [state, setState] = useState<{
    key: string;
    data?: T;
    error?: string;
    loading: boolean;
  }>(null);
  useEffect(() => {
    if (!key) {
      setState(null);
      return;
    }
    const controller = new AbortController();
    let active = true;
    setState({ key, loading: true });
    loader
      .current(controller.signal)
      .then((data) => {
        if (active) setState({ key, data, loading: false });
      })
      .catch((error) => {
        if (active && error?.name !== 'AbortError')
          setState({ key, error: maintenanceErrorKey(error), loading: false });
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [key]);
  // Never render the prior request's result while the next effect is being scheduled.
  return state?.key === key
    ? state
    : { key, loading: !!key, data: undefined, error: undefined };
}

export function AssetPicker({
  value,
  onChange,
  companyId,
  onPendingChange,
  required = false
}: {
  value: number | null;
  onChange: (id: number | null) => void;
  companyId: number;
  onPendingChange: (pending: boolean) => void;
  required?: boolean;
}) {
  const mt = useMaintenanceText();
  const [term, setTerm] = useState('');
  const [inputValue, setInputValue] = useState('');
  const [pending, setPending] = useState(false);
  const pendingRef = useRef(false);
  const updatePending = (next: boolean) => {
    pendingRef.current = next;
    setPending(next);
    onPendingChange(next);
  };
  const assetLabel = (asset: MaintenanceAssetOption) =>
    `${asset.name}${asset.customId ? ` · ${asset.customId}` : ''}`;
  const [options, setOptions] = useState<MaintenanceAssetOption[]>([]);
  const [selected, setSelected] = useState<MaintenanceAssetOption>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>(null);
  const [total, setTotal] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setLoading(true);
    setError(null);
    setOptions([]);
    const timer = window.setTimeout(() => {
      searchMaintenanceAssets(term, companyId, controller.signal)
        .then((page) => {
          if (active) {
            setOptions(page.content);
            setTotal(page.totalElements);
          }
        })
        .catch((e) => {
          if (active && e.name !== 'AbortError')
            setError(maintenanceErrorKey(e));
        })
        .finally(() => {
          if (active) setLoading(false);
        });
    }, 250);
    return () => {
      active = false;
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [companyId, term]);
  useEffect(() => {
    if (!value) {
      setSelected(null);
      if (!pendingRef.current) setInputValue('');
      return;
    }
    const controller = new AbortController();
    let active = true;
    getMaintenanceAsset(value, controller.signal)
      .then((asset) => {
        if (active) {
          setSelected(asset);
          if (!pendingRef.current) setInputValue(assetLabel(asset));
        }
      })
      .catch((e) => {
        if (active && e.name !== 'AbortError') {
          setSelected(null);
          setError(maintenanceErrorKey(e));
        }
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [value, companyId]);
  const visibleSelected = selected?.id === value ? selected : null;
  const choices =
    !pending &&
    visibleSelected &&
    !options.some((o) => o.id === visibleSelected.id)
      ? [visibleSelected, ...options]
      : options;
  return (
    <Autocomplete<MaintenanceAssetOption, false, false, false>
      fullWidth
      options={choices}
      value={pending ? null : visibleSelected}
      inputValue={inputValue}
      clearOnBlur={false}
      loading={loading}
      filterOptions={(items) => items}
      getOptionLabel={assetLabel}
      isOptionEqualToValue={(a, b) => a.id === b.id}
      onInputChange={(_event, input, reason) => {
        if (reason === 'input') {
          setInputValue(input);
          setTerm(input);
          updatePending(input.trim().length > 0);
          if (!input.trim()) {
            setSelected(null);
            onChange(null);
          }
        } else if (reason === 'clear') {
          updatePending(false);
          setInputValue('');
          setTerm('');
          setSelected(null);
          onChange(null);
        } else if (!pendingRef.current) {
          setInputValue(input);
        }
      }}
      onChange={(_event, asset) => {
        updatePending(false);
        setSelected(asset);
        setInputValue(asset ? assetLabel(asset) : '');
        setTerm('');
        setError(null);
        onChange(asset?.id || null);
      }}
      noOptionsText={mt('maintenance_no_assets')}
      loadingText={mt('maintenance_loading')}
      renderInput={(params) => (
        <TextField
          {...params}
          label={mt('maintenance_asset')}
          required={required}
          error={!!error}
          helperText={
            error
              ? mt(error)
              : pending
              ? mt('maintenance_asset_pending')
              : total > 50
              ? mt('maintenance_asset_search_limit')
              : mt(
                  required
                    ? 'maintenance_select_asset'
                    : 'maintenance_all_assets_hint'
                )
          }
          InputProps={{
            ...params.InputProps,
            endAdornment: (
              <>
                {loading ? <CircularProgress size={18} /> : null}
                {pending && (
                  <IconButton
                    size="small"
                    aria-label={mt('maintenance_clear_asset_search')}
                    onClick={() => {
                      updatePending(false);
                      setInputValue('');
                      setTerm('');
                      setSelected(null);
                      setError(null);
                      onChange(null);
                    }}
                  >
                    <ClearIcon fontSize="small" />
                  </IconButton>
                )}
                {params.InputProps.endAdornment}
              </>
            )
          }}
        />
      )}
    />
  );
}

export function PeriodFields({
  value,
  onChange
}: {
  value: MaintenancePeriod;
  onChange: (value: MaintenancePeriod) => void;
}) {
  const mt = useMaintenanceText();
  return (
    <>
      <Grid item xs={12} sm={6}>
        <TextField
          fullWidth
          required
          type="datetime-local"
          label={mt('maintenance_start')}
          value={value.start}
          onChange={(e) => onChange({ ...value, start: e.target.value })}
          inputProps={{
            min: '1900-01-01T00:00',
            max: '2200-12-31T23:59',
            step: 60
          }}
          InputLabelProps={{ shrink: true }}
        />
      </Grid>
      <Grid item xs={12} sm={6}>
        <TextField
          fullWidth
          required
          type="datetime-local"
          label={mt('maintenance_end')}
          value={value.end}
          onChange={(e) => onChange({ ...value, end: e.target.value })}
          inputProps={{
            min: '1900-01-01T00:00',
            max: '2200-12-31T23:59',
            step: 60
          }}
          InputLabelProps={{ shrink: true }}
        />
      </Grid>
      <Grid item xs={12}>
        <Typography variant="body2" color="text.secondary">
          {mt('maintenance_interval_hint')}
        </Typography>
        <Stack direction={{ xs: 'column', sm: 'row' }}>
          <FormControlLabel
            control={
              <Checkbox
                checked={value.includeArchived}
                onChange={(e) =>
                  onChange({ ...value, includeArchived: e.target.checked })
                }
              />
            }
            label={mt('maintenance_include_archived')}
          />
          <FormControlLabel
            control={
              <Checkbox
                checked={value.includeDemo}
                onChange={(e) =>
                  onChange({ ...value, includeDemo: e.target.checked })
                }
              />
            }
            label={mt('maintenance_include_demo')}
          />
        </Stack>
      </Grid>
    </>
  );
}

export function QueryActions({
  loading,
  dirty,
  hasRequest,
  blocked = false,
  onRefresh
}: {
  loading: boolean;
  dirty: boolean;
  hasRequest: boolean;
  blocked?: boolean;
  onRefresh: () => void;
}) {
  const mt = useMaintenanceText();
  return (
    <Stack spacing={1}>
      <Stack direction="row" spacing={1}>
        <Button variant="contained" type="submit" disabled={blocked}>
          {mt('maintenance_apply')}
        </Button>
        <Button
          variant="outlined"
          type="button"
          disabled={loading || dirty || blocked || !hasRequest}
          onClick={onRefresh}
        >
          {mt('maintenance_refresh')}
        </Button>
      </Stack>
      {dirty && hasRequest && (
        <Typography variant="body2" color="text.secondary">
          {mt('maintenance_unapplied')}
        </Typography>
      )}
    </Stack>
  );
}

export function QueryFeedback({
  loading,
  error
}: {
  loading: boolean;
  error?: string;
}) {
  const mt = useMaintenanceText();
  if (loading)
    return (
      <Card sx={{ p: 4 }}>
        <Stack direction="row" spacing={2} alignItems="center" role="status">
          <CircularProgress size={24} />
          <Typography>{mt('maintenance_loading')}</Typography>
        </Stack>
      </Card>
    );
  return error ? (
    <Alert severity="error" role="alert">
      {mt(error)}
    </Alert>
  ) : null;
}

export function utcLabel(value: string | null): string {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isFinite(date.getTime())
    ? `${date.toISOString().slice(0, 16).replace('T', ' ')} UTC`
    : '—';
}
export function cycleHours(seconds: number | null): string {
  return seconds === null ||
    seconds === undefined ||
    !Number.isFinite(Number(seconds))
    ? '—'
    : (Number(seconds) / 3600).toLocaleString(undefined, {
        maximumFractionDigits: 2
      });
}
