from pathlib import Path
import tempfile, subprocess,sys,datetime,json
script=Path(__file__).resolve().with_name('summarize-maintenance-tests.py')
with tempfile.TemporaryDirectory(prefix='cmms-summary-gate-') as temp:
 root=Path(temp);start=datetime.datetime.now(datetime.timezone.utc).isoformat();passed=[]
 def run(name,log,expected):
  p=root/'maven.log';p.write_text(log,encoding='utf-8');out=root/'summary.json'
  result=subprocess.run([sys.executable,str(script),'--since',start,'--log',str(p),'--reports',str(root),'--output',str(out),'--min-tests','1','--require-no-skips'],capture_output=True,text=True)
  assert (result.returncode==0)==expected,(name,result.stderr,result.stdout)
  passed.append(name)
 good='[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.1 s -- in sample.Nested\n[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0\n[INFO] BUILD SUCCESS\n'
 run('complete success',good,True)
 run('skipped cases rejected',good.replace('Skipped: 0','Skipped: 1'),False)
 run('failed build rejected',good.replace('BUILD SUCCESS','BUILD FAILURE'),False)
 run('missing final summary rejected',good.replace('[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0\n',''),False)
 run('inconsistent suite/final count rejected',good.replace('Time elapsed:','Time omitted:'),False)
 run('concatenated runs rejected',good+good,False)
 print(json.dumps({'passed':len(passed),'failed':0}))
