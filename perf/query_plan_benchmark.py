"""Controlled SQL-only experiment. Requires psql; only the dedicated local test DB is accepted."""
import argparse, datetime, json, os, pathlib, statistics, subprocess, uuid

parser=argparse.ArgumentParser()
parser.add_argument('--psql',default='psql')
parser.add_argument('--output',required=True)
args=parser.parse_args()
schema='cmms_bench_'+uuid.uuid4().hex
assert schema.startswith('cmms_bench_') and schema[11:].isalnum()
env=os.environ.copy();env['PGPASSWORD']='cmms_test_local'
cmd=[args.psql,'-X','-q','-A','-t','-v','ON_ERROR_STOP=1','-h','127.0.0.1','-p','25432','-U','cmms_test','-d','cmms_enhancement_test']
def sql(statement):
    result=subprocess.run(cmd,input=statement,text=True,capture_output=True,env=env,check=True)
    return result.stdout.strip()

query="""SELECT count(*), count(*) FILTER (WHERE status=3),
avg(EXTRACT(EPOCH FROM (completed_on-created_at))) FILTER (WHERE status=3 AND completed_on IS NOT NULL AND completed_on>=created_at),
count(*) FILTER (WHERE status=3 AND completed_on IS NOT NULL AND completed_on>=created_at),
count(*) FILTER (WHERE status=3 AND (completed_on IS NULL OR completed_on<created_at))
FROM work_order WHERE company_id=1 AND asset_id=1
AND created_at>=TIMESTAMP '2026-01-01' AND created_at<TIMESTAMP '2027-01-01'
AND archived=false AND COALESCE(is_demo,false)=false"""
report={'recordedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'scope':'SQL-only synthetic uniform fixture; not an API, production capacity or Redis benchmark',
        'database':sql('SELECT version()'),'rows':100000,'companies':10,'assets':1000,
        'schema':schema,'query':query,'runs':{}}
created=False
try:
    sql(f'''CREATE SCHEMA {schema}; SET search_path TO {schema};
CREATE TABLE work_order(id bigint PRIMARY KEY,company_id bigint NOT NULL,asset_id bigint,
created_at timestamp NOT NULL,completed_on timestamp,status integer NOT NULL,archived boolean NOT NULL,is_demo boolean);
INSERT INTO work_order SELECT g,(g%10)+1,(g%1000)+1,
TIMESTAMP '2026-01-01'+(g%365)*INTERVAL '1 day',
CASE WHEN (g/1000)%4=3 THEN TIMESTAMP '2026-01-01'+(g%365)*INTERVAL '1 day'+INTERVAL '1 hour' END,
(g/1000)%4,false,false FROM generate_series(1,100000) g;
CREATE INDEX work_order_company_baseline ON work_order(company_id); ANALYZE work_order;''')
    created=True
    before=sql(f'SET search_path TO {schema};{query};')
    for mode in ['company_index','company_asset_created_index']:
        if mode!='company_index':
            sql(f'SET search_path TO {schema}; CREATE INDEX work_order_company_asset_created ON work_order(company_id,asset_id,created_at,id); ANALYZE work_order;')
        plans=[]
        for i in range(4):
            plan=json.loads(sql(f'SET search_path TO {schema};EXPLAIN (ANALYZE,BUFFERS,FORMAT JSON) {query};'))[0]
            if i:plans.append(plan)
        report['runs'][mode]={'warmup':1,'measured':3,'plans':plans,
            'medianExecutionMs':statistics.median(p['Execution Time'] for p in plans)}
    after=sql(f'SET search_path TO {schema};{query};')
    assert before==after, 'Index changed query results'
    report['result']=after;report['identicalResults']=True
    pathlib.Path(args.output).write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({name:value['medianExecutionMs'] for name,value in report['runs'].items()}))
finally:
    if created:
        assert schema.startswith('cmms_bench_') and len(schema)==43
        sql(f'DROP SCHEMA {schema} CASCADE;')
