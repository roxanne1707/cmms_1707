import { TitleContext } from '../../../contexts/TitleContext';
import { Helmet } from 'react-helmet-async';
import { useContext, useEffect, useRef, useState } from 'react';
import { Alert, Box, Button, Card, Dialog, DialogActions, DialogContent, DialogTitle, Link, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow, TextField, Typography } from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import useAuth from '../../../hooks/useAuth';
import api from '../../../utils/api';
import { PermissionEntity } from '../../../models/owns/role';
type Item = { id: number; customId: string; title: string; assetId?: number; assetName?: string };
type Page = { content: Item[]; totalElements: number; page: number; size: number };
function errorText(e: unknown) {
  const status = (e as { status?: number })?.status;
  if (status === 403) return '没有当前企业的报修与工单管理权限。';
  if (status === 409) return '报修或工单状态已经变化，或设备不一致。请刷新后重新选择；不会自动创建新工单。';
  if (status === 404) return '所选报修或工单已不存在，请刷新。';
  if (status === 400) return '请填写 5～500 字符的关联理由。';
  return '请求未完成。请刷新检查关联结果，再决定是否重试。';
}
export default function RequestLink() {
  const { user, company } = useAuth();
  const { setTitle } = useContext(TitleContext);
  useEffect(() => { setTitle('重复报修关联'); }, [setTitle]);
  const role = user?.role;
  const allowed = user?.enabled && company && (role?.viewPermissions?.includes(PermissionEntity.SETTINGS) || role?.code === 'LIMITED_ADMIN') &&
    [PermissionEntity.REQUESTS, PermissionEntity.WORK_ORDERS].every(p => role?.viewPermissions?.includes(p) && role?.viewOtherPermissions?.includes(p));
  return <Box sx={{ p: { xs: 2, md: 3 } }}><Helmet><title>重复报修关联 — Atlas CMMS</title></Helmet><Typography component="h1" variant="h3" gutterBottom>重复报修关联</Typography><Typography color="text.secondary" sx={{ mb: 2 }}>将同一设备的多个待处理报修交给已有维修工单，保留报修记录与关联审计。</Typography>
    {allowed ? <LinkContent key={`${user.id}:${company.id}:${JSON.stringify(role)}`} /> : <Alert severity="warning">需要当前企业的报修及工单管理权限。</Alert>}
  </Box>;
}
function LinkContent() {
  const [pending, setPending] = useState<Page>(null);
  const [candidates, setCandidates] = useState<Page>(null);
  const [selected, setSelected] = useState<Item>(null);
  const [target, setTarget] = useState<Item>(null);
  const [page, setPage] = useState(0);
  const [candidatePage, setCandidatePage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [loading, setLoading] = useState(false);
  const [loadingTargets, setLoadingTargets] = useState(false);
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setPending(null); setError('');
    api.get<Page>(`request-links/pending?page=${page}&size=10`, { signal: controller.signal })
      .then(data => { if (!controller.signal.aborted) setPending(data); })
      .catch(e => { if (!controller.signal.aborted) setError(errorText(e)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [page, revision]);
  useEffect(() => {
    setCandidates(null); if (!selected) return;
    const controller = new AbortController(); setLoadingTargets(true);
    api.get<Page>(`request-links/${selected.id}/candidates?page=${candidatePage}&size=10`, { signal: controller.signal })
      .then(data => { if (!controller.signal.aborted) setCandidates(data); })
      .catch(e => { if (!controller.signal.aborted) setError(errorText(e)); })
      .finally(() => { if (!controller.signal.aborted) setLoadingTargets(false); });
    return () => controller.abort();
  }, [selected?.id, candidatePage, revision]);
  async function link() {
    if (busyRef.current || !selected || !target || reason.trim().length < 5) return;
    busyRef.current = true; setBusy(true); setError(''); setNotice('');
    try {
      const result = await api.post<{ requestId: number; workOrderId: number; replayed: boolean }>(`request-links/${selected.id}`, { workOrderId: target.id, reason: reason.trim() }, { suppressConflictNotification: true });
      setNotice(`${result.replayed ? '该关联已存在' : '关联成功'}：报修 ${selected.customId} → 工单 ${target.customId}。未新建工单，操作已记入审计。`);
      setSelected(null); setTarget(null); setReason(''); setRevision(v => v + 1);
    } catch (e) { setError(errorText(e)); setTarget(null); setRevision(v => v + 1); }
    finally { busyRef.current = false; setBusy(false); }
  }
  return <Stack spacing={2}>
    <Alert severity="info">只允许同企业、同设备、未取消的待处理报修关联未完成且未归档工单。关联后不能继续修改报修；原始来源报修不会被覆盖。</Alert>
    {error && <Alert severity="error">{error}</Alert>}{notice && <Alert severity="success">{notice}</Alert>}
    <Stack direction="row" justifyContent="space-between"><Typography variant="h4">待处理报修</Typography><Button disabled={loading || busy} onClick={() => { setSelected(null); setRevision(v => v + 1); }}>刷新</Button></Stack>
    {loading && <Alert severity="info">正在读取报修…</Alert>}
    <Card><TableContainer><Table sx={{ minWidth: 560 }}><TableHead><TableRow><TableCell>报修</TableCell><TableCell>设备</TableCell><TableCell>操作</TableCell></TableRow></TableHead><TableBody>
      {pending?.content.map(item => <TableRow key={item.id} selected={selected?.id === item.id}><TableCell>{item.customId}<Typography>{item.title}</Typography></TableCell><TableCell>{item.assetName || '未指定设备'}</TableCell><TableCell><Button disabled={busy} onClick={() => { setSelected(item); setCandidatePage(0); setTarget(null); setReason(''); setError(''); }}>选择此报修</Button></TableCell></TableRow>)}
      {!loading && pending?.content.length === 0 && <TableRow><TableCell colSpan={3}>暂无待处理报修。</TableCell></TableRow>}
    </TableBody></Table></TableContainer><TablePagination component="div" count={pending?.totalElements || 0} page={page} rowsPerPage={10} rowsPerPageOptions={[10]} onPageChange={(_, p) => { setPage(p); setSelected(null); }} labelRowsPerPage="每页" /></Card>
    {selected && <><Typography variant="h4">为「{selected.title}」选择已有维修工单</Typography>
      {loadingTargets && <Alert severity="info">正在读取同设备工单…</Alert>}
      <Card><TableContainer><Table sx={{ minWidth: 560 }}><TableHead><TableRow><TableCell>已有工单</TableCell><TableCell>操作</TableCell></TableRow></TableHead><TableBody>{candidates?.content.map(item => <TableRow key={item.id}><TableCell><Link component={RouterLink} to={`/app/work-orders/${item.id}`}>{item.customId}</Link><Typography>{item.title}</Typography></TableCell><TableCell><Button variant="outlined" disabled={busy} onClick={() => { setTarget(item); setReason(''); }}>关联此工单</Button></TableCell></TableRow>)}
        {!loadingTargets && candidates?.content.length === 0 && <TableRow><TableCell colSpan={2}>没有可关联的同设备工单。请使用原报修审批流程创建工单。</TableCell></TableRow>}
      </TableBody></Table></TableContainer><TablePagination component="div" count={candidates?.totalElements || 0} page={candidatePage} rowsPerPage={10} rowsPerPageOptions={[10]} onPageChange={(_, p) => setCandidatePage(p)} labelRowsPerPage="每页" /></Card></>}
    <Dialog open={!!target} onClose={() => { if (!busy) setTarget(null); }} maxWidth="sm" fullWidth><DialogTitle>确认关联已有工单</DialogTitle><DialogContent><Typography sx={{ mb: 2 }}>报修「{selected?.title}」→ 工单「{target?.title}」。将保留两者记录，并记录操作者和理由。</Typography><TextField autoFocus fullWidth multiline minRows={2} label="关联理由（至少 5 字符）" value={reason} onChange={e => setReason(e.target.value)} inputProps={{ maxLength: 500 }} disabled={busy} /></DialogContent><DialogActions><Button disabled={busy} onClick={() => setTarget(null)}>取消</Button><Button variant="contained" disabled={busy || reason.trim().length < 5 || reason.length > 500} onClick={link}>{busy ? '关联中…' : '确认关联'}</Button></DialogActions></Dialog>
  </Stack>;
}
