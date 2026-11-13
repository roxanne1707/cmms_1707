"""Loopback-only portable monitoring. Credentials/data stay outside Git."""
from pathlib import Path
import subprocess,secrets,json,urllib.request,base64,time,socket
import argparse
parser=argparse.ArgumentParser(description='Start loopback-only CMMS lab monitoring with preverified portable tools')
parser.add_argument('--tools-root',required=True,type=Path)
args=parser.parse_args()
repo=Path(__file__).resolve().parent.parent
r=args.tools_root.resolve().parent
tools=args.tools_root.resolve()
runtime=repo/'local-runtime/maintenance-observability';runtime.mkdir(parents=True,exist_ok=True)
prom=tools/'prometheus-3.16.0.windows-amd64/prometheus.exe'
graf=next(tools.glob('grafana*/bin/grafana-server.exe'),None)
if graf is None:graf=next(tools.glob('grafana*/bin/grafana.exe'),None)
assert prom.exists() and graf and graf.exists(),'Portable tools must be verified/extracted first'
for port in (32909,33090):
 with socket.socket() as s:assert s.connect_ex(('127.0.0.1',port))!=0,f'Port {port} occupied; no process changed'
password=(repo/'local-runtime/maintenance-lab-28089/metrics.key').read_text().strip()
(runtime/'scrape.key').write_text(password)
(runtime/'prometheus.yml').write_text(f'''global:
  scrape_interval: 5s
scrape_configs:
  - job_name: cmms-lab
    metrics_path: /actuator/prometheus
    basic_auth:
      username: cmms-metrics
      password_file: {str(runtime/'scrape.key').replace(chr(92),'/')}
    static_configs:
      - targets: ['127.0.0.1:28089']
''')
prov=runtime/'provisioning';(prov/'datasources').mkdir(parents=True,exist_ok=True);(prov/'dashboards').mkdir(exist_ok=True)
(prov/'datasources/cmms.yml').write_text('''apiVersion: 1
datasources:
  - name: CMMS Prometheus
    uid: cmms-prometheus
    type: prometheus
    access: proxy
    url: http://127.0.0.1:32909
    isDefault: true
''')
(prov/'dashboards/cmms.yml').write_text(f'''apiVersion: 1
providers:
  - name: CMMS
    options:
      path: {str(repo/'observability').replace(chr(92),'/')}
''')
for folder in ('grafana-data','grafana-logs','plugins','prometheus-data'):
 (runtime/folder).mkdir(parents=True,exist_ok=True)
admin=json.loads((runtime/'access.json').read_text())['password'] if (runtime/'access.json').exists() else secrets.token_urlsafe(32)
(runtime/'access.json').write_text(json.dumps({'url':'http://127.0.0.1:33090','username':'cmms-lab','password':admin}))
def posix(p):return str(p).replace('\\','/')
(runtime/'grafana.ini').write_text(f'''[server]
http_addr = 127.0.0.1
http_port = 33090
[paths]
data = {posix(runtime/'grafana-data')}
logs = {posix(runtime/'grafana-logs')}
plugins = {posix(runtime/'plugins')}
provisioning = {posix(prov)}
[security]
admin_user = cmms-lab
admin_password = {admin}
[analytics]
reporting_enabled = false
check_for_updates = false
check_for_plugin_updates = false
[plugins]
preinstall_disabled = true
[auth.anonymous]
enabled = false
''')
processes=[]
for name,cmd in [('prometheus',[str(prom),'--config.file='+str(runtime/'prometheus.yml'),'--storage.tsdb.path='+str(runtime/'prometheus-data'),'--web.listen-address=127.0.0.1:32909']),('grafana',[str(graf),'server','--homepath',str(graf.parent.parent),'--config',str(runtime/'grafana.ini')])]:
 log=open(runtime/(name+'.log'),'w');p=subprocess.Popen(cmd,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW);processes.append({'name':name,'pid':p.pid})
(runtime/'processes.json').write_text(json.dumps(processes));print(json.dumps(processes))
