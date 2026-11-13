// Local preview only. Serves the fork build and proxies exclusively to the isolated API.
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const repo = path.resolve(__dirname, '..');
const build = path.join(repo, 'frontend', 'build');
const mysql = process.env.CMMS_PREVIEW_DATABASE === 'mysql';
if (process.env.CMMS_PREVIEW_DATABASE && !['mysql','postgresql'].includes(process.env.CMMS_PREVIEW_DATABASE)) throw new Error('Unsupported isolated preview');
const port = mysql ? 3389 : 3289;
const origin = `http://127.0.0.1:${port}`;
const apiOrigin = mysql ? 'http://127.0.0.1:28189' : 'http://127.0.0.1:28089';
const httpProxy = require(require.resolve('http-proxy', { paths: [path.join(repo, 'frontend')] }));
const proxy = httpProxy.createProxyServer({ target: apiOrigin, changeOrigin: true, ws: true, proxyTimeout: 30000 });
if (!fs.existsSync(path.join(build, 'index.html'))) throw new Error('Build cmms-fork/frontend first.');

function localOrigin(req) {
  return !req.headers.origin || req.headers.origin === origin;
}
function proxyOrigin(proxyReq) { proxyReq.setHeader('Origin', apiOrigin); }
proxy.on('proxyReq', proxyOrigin);
proxy.on('proxyReqWs', proxyOrigin);
proxy.on('error', (_error, _req, res) => {
  if (res && typeof res.writeHead === 'function' && !res.headersSent) {
    res.writeHead(503, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ message: 'The isolated maintenance API is unavailable.' }));
  } else if (res && typeof res.destroy === 'function') res.destroy();
});
const mime = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png', '.ico': 'image/x-icon', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.woff': 'font/woff', '.woff2': 'font/woff2', '.txt': 'text/plain; charset=utf-8' };
const server = http.createServer((req, res) => {
  let pathname;
  try { pathname = decodeURIComponent(new URL(req.url, origin).pathname); }
  catch { res.writeHead(400); res.end(); return; }
  if (pathname.startsWith('/api/')) {
    if (!localOrigin(req)) { res.writeHead(403); res.end(); return; }
    req.url = req.url.slice(4);
    proxy.web(req, res);
    return;
  }
  if (req.method !== 'GET' && req.method !== 'HEAD') { res.writeHead(405); res.end(); return; }
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Cache-Control', 'no-store');
  if (pathname === '/runtime-env.js') {
    res.setHeader('Content-Type', mime['.js']);
    res.end('window.__RUNTIME_CONFIG__ = ' + JSON.stringify({ API_URL: `${origin}/api/`, HOME_URL: `${origin}/`, CLOUD_VERSION: 'false', INVITATION_VIA_EMAIL: 'false', ENABLE_SSO: 'false', LDAP_ENABLED: 'false' }) + ';');
    return;
  }
  const requested = path.resolve(build, '.' + pathname);
  if (!requested.startsWith(build + path.sep) && requested !== build) { res.writeHead(403); res.end(); return; }
  const file = fs.existsSync(requested) && fs.statSync(requested).isFile() ? requested : path.extname(pathname) ? null : path.join(build, 'index.html');
  if (!file) { res.writeHead(404); res.end(); return; }
  res.setHeader('Content-Type', mime[path.extname(file)] || 'application/octet-stream');
  if (req.method === 'HEAD') { res.end(); return; }
  fs.createReadStream(file).on('error', () => res.destroy()).pipe(res);
});
// A browser may reset an upgraded socket after logout or navigation. Handle that
// connection locally; never suppress process-wide exceptions.
server.on('connection', socket => socket.on('error', () => socket.destroy()));
server.on('clientError', (_error, socket) => socket.destroy());
proxy.on('open', socket => socket.on('error', () => socket.destroy()));
server.on('upgrade', (req, socket, head) => {
  if (!localOrigin(req) || !req.url.startsWith('/api/')) { socket.destroy(); return; }
  req.url = req.url.slice(4);
  proxy.ws(req, socket, head);
});
server.listen(port, '127.0.0.1', () => console.log(`Maintenance preview ${origin}; isolated API ${apiOrigin}; build ${build}`));
process.on('SIGTERM', () => server.close(() => process.exit(0)));
