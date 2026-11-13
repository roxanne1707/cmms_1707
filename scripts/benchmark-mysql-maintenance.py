from pathlib import Path
import subprocess,json,uuid,datetime,re,statistics,argparse
parser=argparse.ArgumentParser(description="Only creates a fresh cmms_mysql_perf_* database on loopback :23306; never changes a deployment")
parser.add_argument("--mysql",required=True,help="mysql client executable")
parser.add_argument("--client-config",required=True,help="Private client option file; use the isolated test account, never commit credentials")
args=parser.parse_args()
repo=Path(__file__).resolve().parents[1]
out=repo/'local-runtime/mysql-benchmark';out.mkdir(parents=True,exist_ok=True)
schema='cmms_mysql_perf_'+uuid.uuid4().hex
cmd=[args.mysql,'--defaults-extra-file='+str(Path(args.client_config).resolve()),'--protocol=TCP','--host=127.0.0.1','--port=23306','--batch','--skip-column-names','--default-character-set=utf8mb4']
def sql(query,db=True):
 p=subprocess.run(cmd+([schema] if db else []),input=query,text=True,capture_output=True,encoding='utf-8',errors='replace')
 if p.returncode:raise RuntimeError(p.stderr[-1200:])
 return p.stdout.strip()
assert sql('SELECT @@port',False)=='23306'
sql('CREATE DATABASE '+schema+' CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs',False)
sql((repo/'api/src/main/resources/db/mysql/schema.sql').read_text(encoding='utf-8'))
# This is a dedicated SQL benchmark database without an application or accounts. No real data, license flags or subscriptions.
sql("INSERT INTO company(id,created_at,updated_at,employees_count,name) VALUES(1,NOW(6),NOW(6),1,'Synthetic A'),(2,NOW(6),NOW(6),1,'Synthetic B'); INSERT INTO asset(id,created_at,updated_at,archived,name,company_id,is_demo) VALUES(101,NOW(6),NOW(6),false,'Hot pump',1,false),(102,NOW(6),NOW(6),false,'Sparse pump',1,false),(201,NOW(6),NOW(6),false,'Other tenant',2,false);")
digits='(SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9)'
numbers='SELECT a.d+10*b.d+100*c.d+1000*d.d+10000*e.d n FROM '+' CROSS JOIN '.join(digits+' '+x for x in 'abcde')
sql("INSERT INTO work_order(id,created_at,updated_at,estimated_duration,required_signature,title,archived,completed_on,status,company_id,asset_id,is_demo) SELECT n+1000,TIMESTAMPADD(MINUTE,n,'2026-01-01'),TIMESTAMPADD(MINUTE,n,'2026-01-01'),0,false,'Synthetic repair',false,CASE WHEN MOD(n,4)=3 THEN TIMESTAMPADD(MINUTE,n+60,'2026-01-01') ELSE NULL END,MOD(n,4),CASE WHEN n<50000 THEN 1 ELSE 2 END,CASE WHEN n<100 THEN 102 WHEN n<50000 THEN 101 ELSE 201 END,false FROM ("+numbers+") x; ANALYZE TABLE work_order;")
source=(repo/'api/src/main/java/com/grash/service/query/AssetWorkOrderOverviewRepository.java').read_text(encoding='utf-8')
query=re.search(r'private static final String MYSQL_SQL = """(.*?)""";',source,re.S).group(1).replace(' FORCE INDEX(ix_work_order_overview_cover)','')
query=query.replace(':company','1').replace(':start',"'2026-01-01'").replace(':end',"'2027-01-01'").replace(':archived','false').replace(':demo','false')
runs=[]
for label,asset,count in [('sparse',102,100),('hot',101,49900)]:
 q=query.replace(':asset',str(asset))
 expected=sql(q);assert int(expected.split('\t')[0])==count
 for repeat in range(5):
  for mode in (['optimizer','full_scan'] if repeat%2==0 else ['full_scan','optimizer']):
   statement=q if mode=='optimizer' else q.replace('FROM work_order','FROM work_order FORCE INDEX(PRIMARY)')
   assert sql(statement)==expected
   plan=sql('EXPLAIN ANALYZE '+statement)
   times=re.findall(r'actual time=[\d.]+\.\.([\d.]+)',plan)
   assert times,plan
   runs.append({'cohort':label,'mode':mode,'repeat':repeat+1,'rootActualMs':float(times[0]),'plan':plan})
beforeRuns=list(runs)
sql((repo/'api/src/main/resources/db/mysql/overview-index.sql').read_text(encoding='utf-8')+' ANALYZE TABLE work_order;')
for label,asset,count in [('sparse',102,100),('hot',101,49900)]:
 q=query.replace(':asset',str(asset)).replace('FROM work_order','FROM work_order FORCE INDEX(ix_work_order_overview_cover)')
 assert int(sql(q).split('\t')[0])==count
 for repeat in range(5):
  plan=sql('EXPLAIN ANALYZE '+q)
  assert 'Covering index' in plan and 'ix_work_order_overview_cover' in plan
  runs.append({'cohort':label,'mode':'covering','repeat':repeat+1,'rootActualMs':float(re.findall(r'actual time=[\d.]+\.\.([\d.]+)',plan)[0]),'plan':plan})
result={'recordedAtUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'version':sql('SELECT VERSION()'),'database':schema,'rows':100000,'purpose':'Compare the same overview SQL before and after the actual covering-index migration plus a targeted index hint. Forced full scan is a diagnostic control, not the previous product implementation. Single-session EXPLAIN ANALYZE excludes HTTP/Redis/auth/network. No triggers during bulk generation, no application on this database.','runs':runs,'summary':{label:{mode:statistics.median(x['rootActualMs'] for x in runs if x['cohort']==label and x['mode']==mode) for mode in ['optimizer','full_scan','covering']} for label in ['sparse','hot']}}
(out/'mysql-sql-results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(result['summary']))
