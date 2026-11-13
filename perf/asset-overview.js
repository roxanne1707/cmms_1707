// Isolated lab only. Tokens and generated asset IDs come from the environment.
import http from 'k6/http';
import { check, sleep } from 'k6';
const bases = (__ENV.LAB_URLS || __ENV.LAB_URL || 'http://127.0.0.1:28089').split(',');
if (bases.some(url => !/^http:\/\/127\.0\.0\.1:(28089|28090)$/.test(url)))
  throw new Error('Use only the dedicated loopback query labs on 28089/28090');
if (!__ENV.LAB_TOKEN || !/^\d+$/.test(__ENV.ASSET_ID || '') || !__ENV.START || !__ENV.END)
  throw new Error('LAB_TOKEN, numeric ASSET_ID, START and END are required');
const start = new Date(__ENV.START), end = new Date(__ENV.END);
if (!Number.isFinite(start.getTime()) || !Number.isFinite(end.getTime()) ||
    end <= start || end - start > 366 * 86400000) throw new Error('Use a valid half-open interval <=366 days');
const body = JSON.stringify({
  start: start.toISOString(), end: end.toISOString(),
  includeArchived: __ENV.INCLUDE_ARCHIVED !== 'false',
  includeDemo: __ENV.INCLUDE_DEMO === 'true'
});
const company = __ENV.COMPANY_ID ? '?companyId=' + encodeURIComponent(__ENV.COMPANY_ID) : '';
export const options = {
  vus: Number(__ENV.VUS || 5), duration: __ENV.DURATION || '30s',
  thresholds: { http_req_failed: ['rate<0.01'], checks: ['rate==1'] }
};
export default function () {
  const base = bases[__ITER % bases.length];
  const response = http.post(base + '/query/assets/' + __ENV.ASSET_ID + '/work-order-overview' + company, body, {
    headers: { Authorization: 'Bearer ' + __ENV.LAB_TOKEN, 'Content-Type': 'application/json' },
    tags: { name: 'asset-work-order-overview' }
  });
  check(response, {
    'authorized query succeeds': r => r.status === 200,
    'device cohort contract matches': r => {
      if (r.status !== 200) return false;
      try { const value = r.json(); return value.assetId === Number(__ENV.ASSET_ID) &&
        value.metricVersion === 'creation-cohort-cycle-v1' && value.total >= value.complete; }
      catch (_) { return false; }
    }
  });
  sleep(0.1);
}
