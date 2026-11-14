"""Run only on a fresh GitHub Linux runner, with private generated credentials.

No database ports, host data mounts, external delivery or production destinations.
Only this run's validated Compose project may be removed.
"""
import base64, concurrent.futures, datetime, ipaddress, json, math, os, re, secrets, subprocess, time, urllib.request, urllib.error
import traceback
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
config={'CI_DB_PASSWORD':secrets.token_hex(24),'CI_METRICS_PASSWORD':secrets.token_hex(24),'CI_ADMIN_EMAIL':'container-bootstrap@example.invalid',
        'CI_ADMIN_PASSWORD':admin_password,'CI_JWT_SECRET':base64.b64encode(secrets.token_bytes(48)).decode(),
        'CI_IMAGE_TAG':os.environ['GITHUB_SHA'][:12],'CI_LIMIT_SECRET':secrets.token_hex(32)}
env_file.write_text(''.join(f'{k}={v}\n' for k,v in config.items()),encoding='utf-8')
env_file.chmod(0o600)
compose=['docker','compose','--env-file',str(env_file),'-f',str(repo/'compose.maintenance-verification.yml'),'-p',project]
result={'runId':run_id,'commit':os.environ['GITHUB_SHA'],'startedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'project':project,'checks':[],'result':'FAIL','cleanup':False,'externalDelivery':False}

def command(args,timeout=900):
    with log_file.open('a',encoding='utf-8') as log:
        p=subprocess.run(args,cwd=repo,stdout=log,stderr=subprocess.STDOUT,timeout=timeout)
    if p.returncode: raise RuntimeError(f'Container command failed with exit {p.returncode}')

targets={}
opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
def discover_targets(include_peer=False):
    services=[('app-pg',38089),('app-mysql',38189)]+([('app-pg-peer',38289)] if include_peer else [])
    for service,port in services:
        cid=subprocess.check_output(compose+['ps','-q',service],text=True).strip()
        if not re.fullmatch(r'[a-f0-9]{12,64}',cid):raise ValueError('Expected one running application container')
        info=json.loads(subprocess.check_output(['docker','inspect',cid],text=True))[0]
        labels=info['Config']['Labels']
        assert labels['com.docker.compose.project']==project and labels['com.docker.compose.service']==service
        address=info['NetworkSettings']['Networks'][project+'_default']['IPAddress']
        if not ipaddress.ip_address(address).is_private:raise ValueError('Only this project internal IP is allowed')
        targets[port]=address
    result['transport']='HTTP from ephemeral runner to label-verified internal container IPs, port 8080'

def request(port,path,body=None,token=None,method=None,idempotency_key=None,forwarded_for=None):
    if port not in (38089,38189,38289) or not path.startswith('/') or '://' in path:
        raise ValueError('Only disposable loopback API destinations are allowed')
    headers={'Content-Type':'application/json'}
    if token:headers['Authorization']='Bearer '+token
    if idempotency_key:headers['Idempotency-Key']=idempotency_key
    if forwarded_for:headers['X-Forwarded-For']=forwarded_for
    if path.startswith('/actuator/'):
        headers['Authorization']='Basic '+base64.b64encode(('cmms-metrics:'+config['CI_METRICS_PASSWORD']).encode()).decode()
    data=None if body is None else json.dumps(body).encode()
    method=method or ('POST' if body is not None else 'GET')
    result['lastRequest']={'logicalPort':port,'path':path,'method':method}
    req=urllib.request.Request(f'http://{targets[port]}:8080'+path,data=data,headers=headers,method=method)
    with opener.open(req,timeout=15) as reply:
        return json.load(reply)

def ready(port):
    deadline=time.monotonic()+240
    while time.monotonic()<deadline:
        try:
            if request(port,'/actuator/health').get('status')=='UP':return
        except (urllib.error.URLError,TimeoutError,ConnectionError) as error:
            result['lastReadinessError']={'type':type(error).__name__,'status':getattr(error,'code',None)}
        time.sleep(2)
    raise RuntimeError(f'Health timeout on disposable port {port}')

def login(port):
    return request(port,'/auth/signin',{'email':admin_email,'password':admin_password})['accessToken']

def scalar(engine,query):
    """Read assertions only, through the disposable project, never a host DB port."""
    if not query.startswith('SELECT '):raise ValueError('Only SELECT assertions are accepted')
    if engine=='postgresql':
        args=['postgres','psql','-U','cmms_ci','-d','cmms_container_pg','-At','-c',query]
    elif engine=='mysql':
        args=['mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -ucmms_ci -Dcmms_container_mysql -N -B -e "$1"','cmms-readonly-proof',query]
    else:raise ValueError('Unknown disposable database')
    return int(subprocess.check_output(compose+['exec','-T']+args,text=True).strip())

def await_events(engine,company_id):
    assert type(company_id) is int and company_id>0
    counts={}
    for _ in range(60):
        counts={
            'published':scalar(engine,f"SELECT COUNT(*) FROM domain_event_outbox WHERE company_id={company_id} AND status='PUBLISHED'"),
            'receipts':scalar(engine,f"SELECT COUNT(*) FROM consumer_receipt WHERE company_id={company_id}"),
            'pending':scalar(engine,f"SELECT COUNT(*) FROM event_retry WHERE company_id={company_id} AND state<>'DONE'"),
            'deliveries':scalar(engine,f"SELECT COUNT(*) FROM event_delivery WHERE company_id={company_id}"),
            'unfinishedDeliveries':scalar(engine,f"SELECT COUNT(*) FROM event_delivery WHERE company_id={company_id} AND state<>'SKIPPED'")}
        if counts['published']>=4 and counts['receipts']>=4 and counts['pending']==0 and counts['deliveries']>=1 and counts['unfinishedDeliveries']==0:
            return counts
        time.sleep(2)
    result['eventCountsAtTimeout']=counts
    raise AssertionError('Durable RabbitMQ consumption did not reach the expected terminal state')

def retention_verification(tokens,saved):
    """Age only this run's completed synthetic event data, inside its owned DB containers."""
    for port,engine in ((38089,'postgresql'),(38189,'mysql')):
        token=tokens[port];company=request(port,'/auth/me',token=token)['companyId']
        assert type(company) is int and company>0
        worker=saved[port][4]
        try:
            request(port,'/reliable-events/retention',token=worker)
            raise AssertionError('Technician must not access retention administration')
        except urllib.error.HTTPError as denied:assert denied.code==403
        preview=request(port,'/reliable-events/retention/runs',{'dryRun':True,'reason':'Preview current synthetic repair events'},token)
        assert preview['candidates']==0
        if engine=='postgresql':
            old="CURRENT_TIMESTAMP - INTERVAL '100 days'"
        else:old="TIMESTAMPADD(DAY,-100,CURRENT_TIMESTAMP(6))"
        sql=';'.join([
            f"UPDATE consumer_receipt SET completed_at={old} WHERE company_id={company}",
            f"UPDATE event_retry SET updated_at={old} WHERE company_id={company} AND state='DONE'",
            f"UPDATE domain_event_outbox SET published_at={old} WHERE company_id={company} AND status='PUBLISHED'",
            f"UPDATE event_delivery SET next_attempt_at={old} WHERE company_id={company} AND state IN ('SENT','SKIPPED')"])
        if engine=='postgresql':args=['postgres','psql','-U','cmms_ci','-d','cmms_container_pg','-v','ON_ERROR_STOP=1','-c',sql]
        else:args=['mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -ucmms_ci -Dcmms_container_mysql -e "$1"','cmms-owned-fixture',sql]
        command(compose+['exec','-T']+args,30)
        before=scalar(engine,f'SELECT COUNT(*) FROM consumer_receipt WHERE company_id={company}')
        preview=request(port,'/reliable-events/retention/runs',{'dryRun':True,'reason':'Preview aged synthetic repair events'},token)
        assert preview['candidates']==before and before>=4
        assert scalar(engine,f'SELECT COUNT(*) FROM event_archive_summary WHERE company_id={company}')==0
        start=time.monotonic()
        execution=request(port,'/reliable-events/retention/runs',{'dryRun':False,'reason':'Retire only synthetic completed notification payloads'},token)
        result.setdefault('retentionExecution',{})[engine]=execution
        elapsed=(time.monotonic()-start)*1000
        assert execution['archived']==before and execution['failed']==0
        assert scalar(engine,f'SELECT COUNT(*) FROM consumer_receipt WHERE company_id={company}')==before
        assert scalar(engine,f'SELECT COUNT(*) FROM domain_event_outbox WHERE company_id={company}')==0
        assert scalar(engine,f'SELECT COUNT(*) FROM event_retry WHERE company_id={company}')==0
        assert scalar(engine,f"SELECT COUNT(*) FROM event_delivery WHERE company_id={company} AND payload<>'{{}}'")==0
        repeat=request(port,'/reliable-events/retention/runs',{'dryRun':False,'reason':'Verify completed retirement is idempotent'},token)
        assert repeat['archived']==0
        result.setdefault('retention',{})[engine]={'archived':before,'receiptsRetained':before,'retiredPayloadBytes':execution['retiredPayloadBytes'],
            'elapsedMs':round(elapsed,3),'previewNonDestructive':True,'repeatArchived':0,'technicianDenied':True}
        result['checks'].append(f'{engine}: tenant-admin retention preview, execution and repeat; receipts retained; technician denied')
        if engine=='postgresql':
            # TECHNICIAN intentionally has no REQUESTS-create permission. Use the normal role API
            # to turn this synthetic user into a REQUESTER for the separate admission-control test.
            identity=request(port,'/auth/me',token=worker)
            requester=next(role for role in request(port,'/roles',token=token) if role['code']=='REQUESTER')
            request(port,f"/users/{identity['id']}/role?role={requester['id']}",token=token,method='PATCH')
            fresh=request(port,'/auth/signin',{'email':'container-technician@example.invalid','password':admin_password})['accessToken']
            assert request(port,'/auth/me',token=fresh)['role']['code']=='REQUESTER'
            saved[port]=(*saved[port][:4],fresh)

def shared_limit_verification(saved):
    """Two REAL API processes, same owned PG/Redis, no license fixtures or host database."""
    print('Verifying shared request admission across two API containers',flush=True)
    command(compose+['up','-d','--no-build','app-pg-peer'],180)
    discover_targets(include_peer=True);ready(38289)
    token=login(38089);me=request(38089,'/auth/me',token=token)
    asset_id,_,_,_,worker_token=saved[38089]
    company_id=me['companyId'];assert type(company_id) is int
    def submit(i):
        port=38089 if i%2==0 else 38289;start=time.monotonic()
        try:
            request(port,'/requests',{'title':f'Shared limit isolated {i}','asset':{'id':asset_id},'priority':'MEDIUM'},token)
            status=200
        except urllib.error.HTTPError as e:
            status=e.code
            with e:body=json.load(e)
            assert status==429 and body['error']=='MAINTENANCE_RATE_LIMITED'
            assert int(e.headers['Retry-After'])>=1
        return {'status':status,'elapsedMs':(time.monotonic()-start)*1000,'logicalPort':port}
    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:
        samples=list(pool.map(submit,range(40)))
    assert sum(s['status']==200 for s in samples)==12
    assert sum(s['status']==429 for s in samples)==28
    assert scalar('postgresql',f"SELECT COUNT(*) FROM request WHERE company_id={company_id} AND title LIKE 'Shared limit isolated %'")==12
    # A separate user still has a budget; the shared company gate has remaining capacity.
    request(38289,'/requests',{'title':'Worker independent request budget','asset':{'id':asset_id}},worker_token)
    command(compose+['restart','app-pg-peer'],120)
    discover_targets(include_peer=True);ready(38289)
    assert submit(41)['status']==429
    # Only the run-owned Redis server is paused. Existing Windows Redis is never contacted.
    command(compose+['exec','-T','redis','redis-cli','CLIENT','PAUSE','2000','ALL'],15)
    started=time.monotonic()
    try:
        request(38089,'/requests',{'title':'Must not be persisted during Redis pause'},token)
        raise AssertionError('Protected write admitted without Redis')
    except urllib.error.HTTPError as e:
        assert e.code==503
        with e:assert json.load(e)['error']=='MAINTENANCE_LIMIT_UNAVAILABLE'
    failure_ms=(time.monotonic()-started)*1000;assert failure_ms<2500
    assert request(38089,f'/assets/{asset_id}',token=token)['id']==asset_id
    time.sleep(2.2);assert submit(43)['status']==429
    before=scalar('postgresql',f'SELECT COUNT(*) FROM request WHERE company_id={company_id}')
    anonymous=[]
    for i in range(10):
        try:
            request(38089 if i%2==0 else 38289,'/requests/portal/not-a-real-licensed-portal',{'title':'Must not bypass portal license'},forwarded_for=f'203.0.113.{i+1}')
            raise AssertionError('Missing or unlicensed portal accepted')
        except urllib.error.HTTPError as e:
            anonymous.append(e.code)
            with e:body=json.load(e)
            if e.code==429:assert body['error']=='MAINTENANCE_RATE_LIMITED'
            else:assert e.code in (403,404,406)
    assert anonymous.count(429)==4 and all(s!=503 for s in anonymous)
    assert scalar('postgresql',f'SELECT COUNT(*) FROM request WHERE company_id={company_id}')==before
    def latency(status):
        values=sorted(s['elapsedMs'] for s in samples if s['status']==status)
        return {'count':len(values),'p95Ms':round(values[math.ceil(len(values)*0.95)-1],3),'p99Ms':round(values[math.ceil(len(values)*0.99)-1],3)}
    result['sharedRateLimit']={'result':'PASS','apiInstances':2,'sharedDatabase':'postgresql','requests':40,'workers':10,
        'allowed':latency(200),'limited':latency(429),'redisPause503Ms':round(failure_ms,3),'restartPreservesQuota':True,
        'otherUserAllowed':True,'untrustedForwardedHeaderCannotMultiplyBudget':True,'missingPortalNeverCreatedRequest':True,
        'scope':'Short synthetic admission test; latency percentiles are small-sample observations, not production capacity'}
    result['checks'].extend(['shared limiter: 2 API instances accept exactly 12 of 40 writes',
        'shared limiter: user isolation and app restart retain shared budget',
        'shared limiter: Redis pause rejects protected write with 503; regular asset read works',
        'shared limiter: forged forwarded addresses do not bypass budget; missing portal cannot create a request'])

try:
    result['docker']=subprocess.check_output(['docker','version','--format','{{.Server.Version}}'],text=True).strip()
    result['compose']=subprocess.check_output(['docker','compose','version','--short'],text=True).strip()
    print('Building the actual backend Dockerfile',flush=True)
    command(compose+['build','app-pg'],1200)
    result['checks'].append('executable Docker image built')
    print('Starting fresh isolated services and both database backends',flush=True)
    command(compose+['up','-d','--no-build'],360)
    discover_targets()
    saved={}
    for engine,port in [('postgresql',38089),('mysql',38189)]:
        ready(port)
        result['checks'].append(f'{engine}: authenticated health is UP')
        try:request(port,'/auth/me');raise AssertionError('Anonymous identity endpoint accepted')
        except urllib.error.HTTPError as e:
            assert e.code in (401,403)
        signup=request(port,'/auth/signup',{'email':admin_email,'password':admin_password,
            'firstName':'Container','lastName':'Verifier','phone':'00000000000',
            'companyName':'Isolated maintenance verification','employeesCount':1,'timeZone':'UTC'})
        assert signup['success']
        token=login(port);me=request(port,'/auth/me',token=token);assert me['email']==admin_email
        worker_email='container-technician@example.invalid'
        technician_role=next(role for role in request(port,'/roles',token=token) if role['code']=='TECHNICIAN')
        invitation=request(port,'/users/invite',{'role':{'id':technician_role['id']},'emails':[worker_email],'disableSendingEmail':True},token)
        assert invitation['success']
        worker_signup=request(port,'/auth/signup',{'email':worker_email,'password':admin_password,
            'firstName':'Container','lastName':'Technician','phone':'00000000001','role':{'id':technician_role['id']}})
        assert worker_signup['success']
        worker_token=request(port,'/auth/signin',{'email':worker_email,'password':admin_password})['accessToken']
        worker=request(port,'/auth/me',token=worker_token)
        assert worker['companyId']==me['companyId']
        assert worker['role']['code']=='TECHNICIAN'
        name=f'Container maintenance pump {engine}'
        asset=request(port,'/assets',{'name':name,'status':'OPERATIONAL'},token)
        work=request(port,'/work-orders',{'title':'Container repair verification','asset':{'id':asset['id']},'priority':'MEDIUM','primaryUser':{'id':worker['id']}},token)
        assert request(port,f"/assets/{asset['id']}",token=token)['name']==name
        assert request(port,f"/work-orders/{work['id']}",token=token)['id']==work['id']
        now=datetime.datetime.now(datetime.timezone.utc)
        query={'start':(now-datetime.timedelta(days=1)).isoformat(),'end':(now+datetime.timedelta(days=1)).isoformat()}
        overview=request(port,f"/query/assets/{asset['id']}/work-order-overview",query,token)
        assert overview['total']==1
        ledger=request(port,'/query/maintenance-ledger',{**query,'assetId':asset['id']},token)
        assert ledger['total']==1 and ledger['content'][0]['id']==work['id']
        result['checks'].append(f'{engine}: overview and MyBatis maintenance ledger match committed work order')
        part=request(port,'/parts',{'name':'Isolated pump seal','quantity':10,'cost':5,'minQuantity':1},token)
        progress=request(port,f"/work-orders/{work['id']}/change-status",{'status':'IN_PROGRESS'},worker_token,method='PATCH')
        assert progress['status']=='IN_PROGRESS'
        consume={'quantity':2,'part':{'id':part['id']},'workOrder':{'id':work['id']}}
        quantity=request(port,'/part-quantities',consume,worker_token,idempotency_key='container-consume-seal-1')
        replay=request(port,'/part-quantities',consume,worker_token,idempotency_key='container-consume-seal-1')
        assert quantity['id']==replay['id'] and replay['quantity']==2
        assert request(port,f"/parts/{part['id']}",token=token)['quantity']==8
        allocated=request(port,f"/part-quantities/work-order/{work['id']}",token=token)
        assert len(allocated)==1 and allocated[0]['quantity']==2
        completed=request(port,f"/work-orders/{work['id']}/change-status",{'status':'COMPLETE','feedback':'Pump seal replaced; isolated verification complete'},worker_token,method='PATCH')
        assert completed['status']=='COMPLETE'
        overview=request(port,f"/query/assets/{asset['id']}/work-order-overview",query,token)
        ledger=request(port,'/query/maintenance-ledger',{**query,'assetId':asset['id']},token)
        assert overview['total']==1 and overview['complete']==1
        assert ledger['total']==1 and ledger['content'][0]['status']=='COMPLETE'
        counts=await_events(engine,me['companyId'])
        notices=request(port,'/notifications',token=worker_token)
        assignments=[n for n in notices if n.get('resourceId')==work['id'] and n.get('notificationType')=='WORK_ORDER']
        assert len(assignments)==1
        assert type(worker['id']) is int and type(work['id']) is int
        assert scalar(engine,f"SELECT COUNT(*) FROM notification WHERE user_id={worker['id']} AND resource_id={work['id']} AND event_purpose='ASSIGNMENT'")==1
        result.setdefault('maintenanceLoop',{})[engine]={'stockBefore':10,'stockAfter':8,'consumed':2,'duplicateCommandSameResult':True,
            'status':'COMPLETE','overviewComplete':overview['complete'],'assignmentNotifications':len(assignments),'events':counts}
        result['checks'].extend([f'{engine}: assigned technician starts and completes repair',
            f'{engine}: part consumption replay changes stock once (10 -> 8)',
            f'{engine}: overview invalidation and ledger reflect COMPLETE',
            f'{engine}: real RabbitMQ confirms, consumer receipts and one assignment notification; outbound delivery SKIPPED'])
        saved[port]=(asset['id'],work['id'],name,part['id'],worker_token)
        result['checks'].extend([f'{engine}: Liquibase boot/health',f'{engine}: anonymous rejection and authenticated identity',
                                 f'{engine}: create/read asset and linked work order'])
    print('Restarting app containers and checking persisted maintenance data',flush=True)
    retention_tokens={}
    command(compose+['restart','app-pg','app-mysql'],120)
    discover_targets()
    for port,(asset_id,work_id,name,part_id,worker_token) in saved.items():
        ready(port);token=login(port)
        retention_tokens[port]=token
        assert request(port,f'/assets/{asset_id}',token=token)['name']==name
        persisted=request(port,f'/work-orders/{work_id}',token=token)
        assert persisted['asset']['id']==asset_id and persisted['status']=='COMPLETE'
        assert request(port,f'/parts/{part_id}',token=token)['quantity']==8
        assert len(request(port,f'/part-quantities/work-order/{work_id}',token=token))==1
        notices=request(port,'/notifications',token=worker_token)
        assert len([n for n in notices if n.get('resourceId')==work_id and n.get('notificationType')=='WORK_ORDER'])==1
        result['checks'].append(f'port {port}: completed repair, stock, part allocation and unique notification survive app restart')
    retention_verification(retention_tokens,saved)
    shared_limit_verification(saved)
    images=json.loads(subprocess.check_output(compose+['images','--format','json'],text=True))
    result['images']=images
    result['result']='PASS'
except Exception as e:
    # Exception bodies and application logs can contain passwords. Record type only.
    result['errorType']=type(e).__name__
    result['failureLocations']=[{'function':frame.name,'line':frame.lineno} for frame in traceback.extract_tb(e.__traceback__) if Path(frame.filename).name==Path(__file__).name]
    if isinstance(e,urllib.error.HTTPError):result['httpStatus']=e.code
    print('Container verification failed; collecting safe exception locations',flush=True)
finally:
    try:
        status=subprocess.check_output(compose+['ps','--all','--format','json'],text=True)
        rows=json.loads(status) if status.lstrip().startswith('[') else [json.loads(line) for line in status.splitlines() if line.strip()]
        result['containerStates']=[{k:row.get(k) for k in ('Service','State','Health','ExitCode')} for row in rows]
    except Exception:pass
    try:command(compose+['logs','--no-color'],30)
    except Exception:pass
    if log_file.exists():
        raw=log_file.read_text(encoding='utf-8',errors='replace')
        result['bootCompletedCount']=len(re.findall(r'Started ApiApplication in ',raw))
        result['boundPorts']=re.findall(r'Tomcat started on port ([0-9]+)',raw)
        result['diagnosticFlags']={'mysqlTriggerBinlogPrivilege':'log_bin_trust_function_creators' in raw}
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
