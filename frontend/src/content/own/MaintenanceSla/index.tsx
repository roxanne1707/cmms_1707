import { TitleContext } from '../../../contexts/TitleContext';
import { Helmet } from 'react-helmet-async';
import { useContext, useEffect, useRef, useState } from 'react';
import { Alert, Box, Button, Card, CardContent, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, Grid, MenuItem, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow, TextField, Typography, Link } from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import api from '../../../utils/api';
import useAuth from '../../../hooks/useAuth';
import { PermissionEntity } from '../../../models/owns/role';

type Priority = 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH';
type Policy = { priority: Priority; responseMinutes: number; repairMinutes: number; enabled: boolean; effectiveFrom?: string };
type Case = { workOrderId: number; customId: string; title: string; priority: Priority; responseDueAt: string; repairDueAt: string; respondedAt: string; completedAt: string; responseBreached: boolean; repairBreached: boolean; responseNotified: boolean; repairNotified: boolean };
type Board = { content: Case[]; totalElements: number; responseBreached: number; repairBreached: number; completed: number };
const priorities: Priority[] = ['NONE', 'LOW', 'MEDIUM', 'HIGH'];
const priorityName = { NONE: '无', LOW: '低', MEDIUM: '中', HIGH: '高' };
const stamp = (value: string) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '尚未记录';
function explain(error: unknown) {
  const status = (error as { status?: number })?.status;
  if (status === 403) return '当前账号没有此企业的维修管理权限。';
  if (status === 404) return 'SLA 模块未启用，或工单不存在。';
  if (status === 409) return '工单须未完成、未归档、非演示数据，且对应优先级策略已启用。';
  if (status === 400) return '请检查时限、工单编号和原因格式。';
  return '请求未完成，请刷新确认结果；不要反复提交。';
}
export default function MaintenanceSla() {
  const { user, company } = useAuth();
  const { setTitle } = useContext(TitleContext);
  useEffect(() => { setTitle('维修工单 SLA'); }, [setTitle]);
  const allowed = user?.enabled && company &&
    (user.role?.viewPermissions?.includes(PermissionEntity.SETTINGS) || user.role?.code === 'LIMITED_ADMIN') &&
    user.role?.viewPermissions?.includes(PermissionEntity.WORK_ORDERS) && user.role?.viewOtherPermissions?.includes(PermissionEntity.WORK_ORDERS);
  return <Box sx={{ p: { xs: 2, md: 3 } }}><Helmet><title>维修工单 SLA — Atlas CMMS</title></Helmet>
    <Typography component="h1" variant="h3" gutterBottom>维修工单 SLA</Typography>
    <Typography color="text.secondary" sx={{ mb: 2 }}>按优先级约定响应和首次修复期限，超时后通过可靠通知升级给维护管理员。</Typography>
    {allowed ? <SlaContent key={`${user.id}:${company.id}:${JSON.stringify(user.role)}`} /> : <Alert severity="warning">需要当前企业的维修管理权限。</Alert>}
  </Box>;
}
function SlaContent() {
  const [board, setBoard] = useState<Board>(null);
  const [policies, setPolicies] = useState<Policy[]>([]);
  const [draft, setDraft] = useState<Policy>({ priority: 'HIGH', responseMinutes: 60, repairMinutes: 480, enabled: true });
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [revision, setRevision] = useState(0);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [orderId, setOrderId] = useState('');
  const [reason, setReason] = useState('');
  const [confirm, setConfirm] = useState<'policy' | 'enroll' | null>(null);
  const busyRef = useRef(false);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError(''); setBoard(null);
    Promise.all([
      api.get<Board>(`maintenance-sla/cases?page=${page}&size=${size}`, { signal: controller.signal }),
      api.get<Policy[]>('maintenance-sla/policies', { signal: controller.signal })
    ]).then(([rows, saved]) => { if (!controller.signal.aborted) { setBoard(rows); setPolicies(saved); } })
      .catch(e => { if (!controller.signal.aborted) setError(explain(e)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [page, size, revision]);
  function choose(priority: Priority) { setDraft(policies.find(p => p.priority === priority) || { priority, responseMinutes: 60, repairMinutes: 480, enabled: true }); }
  async function submit() {
    if (busyRef.current || !confirm) return;
    busyRef.current = true; setBusy(true); setError(''); setNotice('');
    try {
      if (confirm === 'policy') {
        await api.patch(`maintenance-sla/policies/${draft.priority}`, { responseMinutes: draft.responseMinutes, repairMinutes: draft.repairMinutes, enabled: draft.enabled });
        setNotice('策略已保存。已纳管工单的期限快照保持不变，新工单使用新策略。');
      } else {
        await api.post(`maintenance-sla/cases/${Number(orderId)}/enroll`, { reason: reason.trim() });
        setNotice('工单已纳管。截止时间从原创建时间计算，历史工单可能立即超时；通知由后台处理。');
        setOrderId(''); setReason('');
      }
      setConfirm(null); setRevision(v => v + 1);
    } catch (e) { setError(explain(e)); setConfirm(null); }
    finally { busyRef.current = false; setBusy(false); }
  }
  const validPolicy = Number.isInteger(draft.responseMinutes) && draft.responseMinutes >= 1 && Number.isInteger(draft.repairMinutes) && draft.repairMinutes >= draft.responseMinutes && draft.repairMinutes <= 525600;
  const validEnrollment = /^\d+$/.test(orderId) && Number.isSafeInteger(Number(orderId)) && Number(orderId) > 0 && reason.trim().length >= 5 && reason.length <= 500;
  return <Stack spacing={2}>
    <Alert severity="info">采用自然时间；暂停状态不停止计时。响应沿用系统“首次维护操作”时间，修复以首次完成为准；重新打开工单不会重置此周期。自动策略不追溯历史或演示工单。</Alert>
    {error && <Alert severity="error">{error}</Alert>}{notice && <Alert severity="success">{notice}</Alert>}
    <Card><CardContent><Typography variant="h4" gutterBottom>优先级策略</Typography>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} alignItems={{ md: 'center' }}>
        <TextField select label="优先级" value={draft.priority} onChange={e => choose(e.target.value as Priority)} sx={{ minWidth: 120 }} disabled={busy}>{priorities.map(p => <MenuItem key={p} value={p}>{priorityName[p]}</MenuItem>)}</TextField>
        <TextField label="响应期限（分钟）" type="number" value={draft.responseMinutes} onChange={e => setDraft(d => ({ ...d, responseMinutes: Number(e.target.value) }))} disabled={busy} inputProps={{ min: 1, max: 525600 }} />
        <TextField label="修复期限（分钟）" type="number" value={draft.repairMinutes} onChange={e => setDraft(d => ({ ...d, repairMinutes: Number(e.target.value) }))} disabled={busy} inputProps={{ min: 1, max: 525600 }} />
        <FormControlLabel control={<Checkbox checked={draft.enabled} onChange={e => setDraft(d => ({ ...d, enabled: e.target.checked }))} disabled={busy} />} label="启用" />
        <Button variant="contained" disabled={!validPolicy || busy} onClick={() => setConfirm('policy')}>保存策略</Button>
      </Stack>
      <Typography sx={{ mt: 2 }} color="text.secondary">已保存：{policies.length ? policies.map(p => `${priorityName[p.priority]}：${p.responseMinutes}/${p.repairMinutes} 分钟${p.enabled ? '' : '（停用）'}`).join('；') : '暂无策略'}</Typography>
    </CardContent></Card>
    <Card><CardContent><Typography variant="h4" gutterBottom>显式纳管已有工单</Typography>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
        <TextField label="工单数据库 ID" value={orderId} onChange={e => setOrderId(e.target.value)} disabled={busy} inputProps={{ inputMode: 'numeric' }} />
        <TextField label="纳管理由（至少 5 字符）" value={reason} onChange={e => setReason(e.target.value)} disabled={busy} inputProps={{ maxLength: 500 }} sx={{ flex: 1 }} />
        <Button variant="outlined" disabled={!validEnrollment || busy} onClick={() => setConfirm('enroll')}>确认纳管</Button>
      </Stack>
    </CardContent></Card>
    <Grid container spacing={2}>{[['纳管工单', board?.totalElements], ['响应超时', board?.responseBreached], ['修复超时', board?.repairBreached], ['已首次完成', board?.completed]].map(([label, value]) => <Grid key={String(label)} item xs={6} md={3}><Card><CardContent><Typography color="text.secondary">{label}</Typography><Typography variant="h3">{value ?? '—'}</Typography></CardContent></Card></Grid>)}</Grid>
    <Stack direction="row" justifyContent="space-between" alignItems="center"><Typography variant="h4">SLA 台账</Typography><Button disabled={loading || busy} onClick={() => setRevision(v => v + 1)}>刷新</Button></Stack>
    {loading && <Alert severity="info">正在读取 SLA 台账…</Alert>}
    <Card><TableContainer><Table sx={{ minWidth: 900 }}><TableHead><TableRow>{['工单', '优先级快照', '响应期限', '修复期限', '首次响应 / 完成', '超时 / 通知状态'].map(t => <TableCell key={t}>{t}</TableCell>)}</TableRow></TableHead><TableBody>
      {board?.content.map(c => <TableRow key={c.workOrderId}><TableCell><Link component={RouterLink} to={`/app/work-orders/${c.workOrderId}`}>{c.customId}</Link><Typography>{c.title}</Typography></TableCell><TableCell>{priorityName[c.priority]}</TableCell><TableCell>{stamp(c.responseDueAt)}</TableCell><TableCell>{stamp(c.repairDueAt)}</TableCell><TableCell>{stamp(c.respondedAt)}<br />{stamp(c.completedAt)}</TableCell><TableCell>{c.responseBreached ? '响应超时' : '响应未超时'} / {c.repairBreached ? '修复超时' : '修复未超时'}<br />{c.responseNotified ? '响应升级已入账' : ''} {c.repairNotified ? '修复升级已入账' : ''}</TableCell></TableRow>)}
      {!loading && board?.content.length === 0 && <TableRow><TableCell colSpan={6}>暂无纳管工单。保存策略后创建新工单，或显式纳管已有工单。</TableCell></TableRow>}
    </TableBody></Table></TableContainer><TablePagination component="div" count={board?.totalElements || 0} page={page} rowsPerPage={size} onPageChange={(_, p) => setPage(p)} rowsPerPageOptions={[10, 25, 50]} onRowsPerPageChange={e => { setSize(Number(e.target.value)); setPage(0); }} labelRowsPerPage="每页" /></Card>
    <Dialog open={!!confirm} onClose={() => { if (!busy) setConfirm(null); }} maxWidth="sm" fullWidth><DialogTitle>{confirm === 'policy' ? '保存 SLA 策略' : '纳管已有工单'}</DialogTitle><DialogContent><Typography>{confirm === 'policy' ? `优先级 ${priorityName[draft.priority]}：响应 ${draft.responseMinutes} 分钟，修复 ${draft.repairMinutes} 分钟，${draft.enabled ? '启用' : '停用'}。已有期限不会重算。` : `将工单 ${orderId} 纳入 SLA，沿用原创建时间计算截止时间，可能立即触发超时升级。理由：${reason}`}</Typography></DialogContent><DialogActions><Button disabled={busy} onClick={() => setConfirm(null)}>取消</Button><Button disabled={busy} variant="contained" onClick={submit}>{busy ? '处理中…' : '确认'}</Button></DialogActions></Dialog>
  </Stack>;
}
