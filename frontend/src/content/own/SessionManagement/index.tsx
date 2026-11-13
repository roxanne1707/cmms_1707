import { useCallback, useContext, useEffect, useRef, useState } from 'react';
import { Helmet } from 'react-helmet-async';
import { Alert, Box, Button, Card, Chip, CircularProgress, Dialog, DialogActions,
  DialogContent, DialogContentText, DialogTitle, Stack, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, Typography } from '@mui/material';
import useAuth from '../../../hooks/useAuth';
import { TitleContext } from '../../../contexts/TitleContext';
import api, { refreshAccessToken } from '../../../utils/api';

type Session = { id: string; createdAt: string; lastRotatedAt: string; expiresAt: string;
  revokedAt: string | null; userAgent: string; current: boolean };
type SessionList = { sessions: Session[]; legacySession: boolean; limit: number };
type Action = { kind: 'all' | 'one'; session?: Session };
const format = (value: string) => new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'short', timeStyle: 'medium', timeZone: 'UTC'
}).format(new Date(value));

function SessionWorkspace() {
  const { logout } = useAuth();
  const [data, setData] = useState<SessionList | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [action, setAction] = useState<Action | null>(null);
  const [busy, setBusy] = useState(false);
  const submitLock = useRef(false);
  const request = useRef<AbortController | null>(null);
  const mounted = useRef(false);
  const load = useCallback(async () => {
    request.current?.abort();
    const controller = new AbortController();
    request.current = controller;
    setLoading(true); setError(''); setData(null);
    try {
      const result = await api.get<SessionList>('auth/sessions', { signal: controller.signal });
      if (!controller.signal.aborted && mounted.current) setData(result);
    } catch (e: any) {
      if (!controller.signal.aborted && mounted.current) setError('无法加载登录会话，请检查登录状态后重试。');
    } finally {
      if (!controller.signal.aborted && mounted.current) setLoading(false);
    }
  }, []);
  useEffect(() => {
    mounted.current = true; load();
    return () => { mounted.current = false; request.current?.abort(); };
  }, [load]);
  const confirm = async () => {
    if (!action || submitLock.current) return;
    submitLock.current = true; setBusy(true); setError(''); setNotice('');
    try {
      if (action.kind === 'all') {
        await api.post('auth/sessions/logout-all', {});
        localStorage.removeItem('accessToken'); localStorage.removeItem('refreshToken');
        window.location.assign('/account/login');
        return;
      }
      if (action.session.current) { await logout(); window.location.assign('/account/login'); return; }
      await api.deletes(`auth/sessions/${encodeURIComponent(action.session.id)}`);
      if (mounted.current) { setAction(null); setNotice('已退出选定设备；该会话的访问令牌和刷新令牌均已撤销。'); await load(); }
    } catch (e: any) {
      if (mounted.current) setError('操作未确认成功。请刷新列表核对状态后再决定是否重试。');
    } finally {
      submitLock.current = false; if (mounted.current) setBusy(false);
    }
  };
  const upgrade = async () => {
    if (submitLock.current) return;
    submitLock.current=true;setBusy(true);setError('');
    try { if (!(await refreshAccessToken())) throw new Error('refresh failed'); await load(); }
    catch { setError('无法升级旧会话，请重新登录。'); }
    finally { submitLock.current=false;setBusy(false); }
  };
  return <Box sx={{ p: { xs: 2, md: 3 } }}>
    <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" spacing={2} mb={2}>
      <Box><Typography component="h1" variant="h3">登录设备与会话</Typography>
        <Typography color="text.secondary" mt={1}>管理维修账号的登录会话。时间使用 UTC；客户端信息由浏览器提供，仅作识别参考。</Typography></Box>
      <Stack direction="row" spacing={1}><Button onClick={load} disabled={loading || busy}>刷新</Button>
        <Button color="error" variant="outlined" onClick={() => setAction({ kind: 'all' })} disabled={busy || loading}>退出全部设备</Button></Stack>
    </Stack>
    {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
    {notice && <Alert severity="success" sx={{ mb: 2 }}>{notice}</Alert>}
    {data?.legacySession && <Alert severity="info" sx={{ mb: 2 }} action={<Button disabled={busy} onClick={upgrade}>升级当前会话</Button>}>
      当前为升级前的旧会话。升级后可单独退出当前设备；旧客户端的退出接口仍保留原有“退出全部设备”语义。
    </Alert>}
    {loading ? <Box role="status" sx={{ p: 4 }}><CircularProgress size={26} /> 正在加载会话</Box> : data && <Card>
      <TableContainer><Table sx={{ minWidth: 720 }} aria-label="登录会话列表"><TableHead><TableRow>
        <TableCell>客户端与会话</TableCell><TableCell>登录时间 / 最近刷新（UTC）</TableCell>
        <TableCell>有效期（UTC）</TableCell><TableCell>状态</TableCell><TableCell>操作</TableCell>
      </TableRow></TableHead><TableBody>
        {data.sessions.map(session => {
          const expired = new Date(session.expiresAt).getTime() <= Date.now();
          const inactive = Boolean(session.revokedAt) || expired;
          return <TableRow key={session.id}><TableCell sx={{ maxWidth: 320, overflowWrap: 'anywhere' }}>
            <Typography>{session.userAgent}</Typography><Typography variant="caption" color="text.secondary">{session.id.slice(0, 8)}</Typography>
            {session.current && <Chip label="当前设备" size="small" color="primary" sx={{ ml: 1 }} />}</TableCell>
            <TableCell>{format(session.createdAt)}<br /><Typography variant="caption">{format(session.lastRotatedAt)}</Typography></TableCell>
            <TableCell>{format(session.expiresAt)}</TableCell><TableCell><Chip size="small" label={session.revokedAt ? '已退出' : expired ? '已过期' : '有效'} color={inactive ? 'default' : 'success'} /></TableCell>
            <TableCell><Button color="error" disabled={inactive || busy} onClick={() => setAction({ kind: 'one', session })}>{session.current ? '退出当前设备' : '退出此设备'}</Button></TableCell>
          </TableRow>;
        })}
        {!data.sessions.length && <TableRow><TableCell colSpan={5}>暂无新版会话记录。旧会话可通过上方按钮升级。</TableCell></TableRow>}
      </TableBody></Table></TableContainer>
      <Typography variant="body2" color="text.secondary" sx={{ p: 2 }}>最多显示最近 {data.limit} 条会话记录。会话代表一次登录，不等同于物理设备；刷新令牌不会新建一条设备记录。</Typography>
    </Card>}
    <Dialog open={Boolean(action)} onClose={() => { if (!busy) setAction(null); }} fullWidth maxWidth="sm">
      <DialogTitle>{action?.kind === 'all' ? '确认退出全部设备' : '确认退出选定设备'}</DialogTitle>
      <DialogContent><DialogContentText>{action?.kind === 'all'
        ? '包括当前浏览器在内的所有登录会话将被撤销，之后需要重新登录。'
        : '该登录会话的访问令牌、刷新令牌和后续实时通知访问将失效，其他登录会话不受影响。'}</DialogContentText></DialogContent>
      <DialogActions><Button disabled={busy} onClick={() => setAction(null)}>取消</Button><Button color="error" variant="contained" disabled={busy} onClick={confirm}>{busy ? '正在退出…' : '确认退出'}</Button></DialogActions>
    </Dialog>
  </Box>;
}

export default function SessionManagement() {
  const { user } = useAuth();
  const { setTitle } = useContext(TitleContext);
  useEffect(() => { setTitle('登录设备与会话'); }, [setTitle]);
  return <><Helmet><title>登录设备与会话 — Atlas CMMS</title></Helmet><SessionWorkspace key={user?.id ?? 'anonymous'} /></>;
}
