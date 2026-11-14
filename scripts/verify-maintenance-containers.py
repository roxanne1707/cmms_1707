"""Run only on a fresh GitHub Linux runner, with private generated credentials.

No database ports, host data mounts, external delivery or production destinations.
Only this run's validated Compose project may be removed.
"""
import base64, datetime, json, os, re, secrets, subprocess, time, urllib.request, urllib.error
from pathlib import Path

if os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('RUNNER_OS') != 'Linux':
    raise SystemExit('Only a disposable GitHub Linux runner is accepted')
run_id=os.environ['GITHUB_RUN_ID'];attempt=os.environ['GITHUB_RUN_ATTEMPT']
if not re.fullmatch(r'[0-9]+',run_id) or not re.fullmatch(r'[0-9]+',attempt):
    raise SystemExit('Invalid run identifier')
project=f'cmms-ci-{run_id}-{attempt}'
repo=Path(__file__).resolve().parents[1];out=Path(os.environ['RUNNER_TEMP'])
env_file=out/'cmms-container.env';log_file=out/'cmms-container-private.log'
summary=out/'cmms-container-summary.json'
admin_email='container-admin@example.invalid';admin_password=secrets.token_urlsafe(30)
config={'CI_DB_PASSWORD':secrets.token_hex(24),'CI_ADMIN_EMAIL':admin_email,
        'CI_ADMIN_PASSWORD':admin_password,'CI_JWT_SECRET':base64.b64encode(secrets.token_bytes(48)).decode(),
        'CI_IMAGE_TAG':os.environ['GITHUB_SHA'][:12]}
env_file.write_text(''.join(f'{k}={v}\n' for k,v in config.items()),encoding='utf-8')
env_file.chmod(0o600)
compose=['docker','compose','--env-file',str(env_file),'-f',str(repo/'compose.maintenance-verification.yml'),'-p',project]
result={'runId':run_id,'commit':os.environ['GITHUB_SHA'],'startedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'project':project,'checks':[],'result':'FAIL','cleanup':False,'externalDelivery':False}

def command(args,timeout=900):
    with log_file.open('a',encoding='utf-8') as log:
        p=subprocess.run(args,cwd=repo,stdout=log,stderr=subprocess.STDOUT,timeout=timeout)
    if p.returncode: raise RuntimeError(f'Container command failed with exit {p.returncode}')

def request(port,path,body=None,token=None):
    if port not in (38089,38189) or not path.startswith('/') or '://' in path:
        raise ValueError('Only disposable loopback API destinations are allowed')
    headers={'Content-Type':'application/json'}
    if token:headers['Authorization']='Bearer '+token
    data=None if body is None else json.dumps(body).encode()
    req=urllib.request.Request(f'http://127.0.0.1:{port}'+path,data=data,headers=headers)
    with urllib.request.urlopen(req,timeout=15) as reply:
        return json.load(reply)

def ready(port):
    deadline=time.monotonic()+240
    while time.monotonic()<deadline:
        try:
            if request(port,'/actuator/health').get('status')=='UP':return
        except (urllib.error.URLError,TimeoutError,ConnectionError):pass
        time.sleep(2)
    raise RuntimeError(f'Health timeout on disposable port {port}')

def login(port):
    return request(port,'/auth/signin',{'email':admin_email,'password':admin_password})['accessToken']

try:
    result['docker']=subprocess.check_output(['docker','version','--format','{{.Server.Version}}'],text=True).strip()
    result['compose']=subprocess.check_output(['docker','compose','version','--short'],text=True).strip()
    print('Building the actual backend Dockerfile',flush=True)
    command(compose+['build','app-pg'],1200)
    result['checks'].append('executable Docker image built')
    print('Starting fresh isolated services and both database backends',flush=True)
    command(compose+['up','-d','--no-build'],360)
    saved={}
    for engine,port in [('postgresql',38089),('mysql',38189)]:
        ready(port)
        try:request(port,'/auth/me');raise AssertionError('Anonymous identity endpoint accepted')
        except urllib.error.HTTPError as e:
            assert e.code in (401,403)
        token=login(port);assert request(port,'/auth/me',token=token)['email']==admin_email
        name=f'Container maintenance pump {engine}'
        asset=request(port,'/assets',{'name':name,'status':'OPERATIONAL'},token)
        work=request(port,'/work-orders',{'title':'Container repair verification','asset':{'id':asset['id']},'priority':'MEDIUM'},token)
        assert request(port,f"/assets/{asset['id']}",token=token)['name']==name
        assert request(port,f"/work-orders/{work['id']}",token=token)['id']==work['id']
        saved[port]=(asset['id'],work['id'],name)
        result['checks'].extend([f'{engine}: Liquibase boot/health',f'{engine}: anonymous rejection and authenticated identity',
                                 f'{engine}: create/read asset and linked work order'])
    print('Restarting app containers and checking persisted maintenance data',flush=True)
    command(compose+['restart','app-pg','app-mysql'],120)
    for port,(asset_id,work_id,name) in saved.items():
        ready(port);token=login(port)
        assert request(port,f'/assets/{asset_id}',token=token)['name']==name
        assert request(port,f'/work-orders/{work_id}',token=token)['asset']['id']==asset_id
        result['checks'].append(f'port {port}: asset/work-order relationship survives app restart')
    images=json.loads(subprocess.check_output(compose+['images','--format','json'],text=True))
    result['images']=images
    result['result']='PASS'
except Exception as e:
    # Exception bodies and application logs can contain passwords. Record type only.
    result['errorType']=type(e).__name__
    print('Container verification failed; collecting safe exception locations',flush=True)
finally:
    try:command(compose+['logs','--no-color'],30)
    except Exception:pass
    if log_file.exists():
        raw=log_file.read_text(encoding='utf-8',errors='replace')
        result['exceptionTypes']=sorted(set(re.findall(r'Caused by: ([A-Za-z0-9_.$]+)',raw)))
        result['schemaErrors']=re.findall(r'Schema-validation: (?:missing|wrong)[^\r\n]{0,160}',raw)[:10]
    try:
        command(compose+['down','--volumes','--remove-orphans'],120)
        result['cleanup']=True
    except Exception:
        result['result']='FAIL'
    env_file.unlink(missing_ok=True)
    result['finishedAt']=datetime.datetime.now(datetime.timezone.utc).isoformat()
    summary.write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps(result))
raise SystemExit(0 if result['result']=='PASS' else 1)
