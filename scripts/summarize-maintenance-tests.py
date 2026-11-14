"""Publish counts only; raw Surefire XML can contain connection credentials."""
import argparse, datetime, json, re
from pathlib import Path
import xml.etree.ElementTree as ET
p=argparse.ArgumentParser()
p.add_argument('--since',required=True)
p.add_argument('--log',required=True)
p.add_argument('--reports',default=str(Path(__file__).resolve().parents[1]/'api/target/surefire-reports'))
p.add_argument('--output',required=True)
p.add_argument('--min-tests',type=int,default=1)
p.add_argument('--require-no-skips',action='store_true')
a=p.parse_args()
start=datetime.datetime.fromisoformat(a.since.strip().replace('Z','+00:00')).timestamp()
totals=dict(tests=0,failures=0,errors=0,skipped=0)
suites=[]
failures=[]
problems=[]
for path in sorted(Path(a.reports).glob('TEST-*.xml')):
    if path.stat().st_mtime < start: continue
    suite=ET.parse(path).getroot()
    row={'suite':suite.attrib['name']}
    for k in totals:
        row[k]=int(suite.attrib.get(k,0));totals[k]+=row[k]
    suites.append(row)
    for case in suite.iter('testcase'):
        for failure in list(case.findall('failure'))+list(case.findall('error')):
            # Deliberately omit exception messages, system properties and raw output.
            # Those can contain database URLs, recipient addresses or credentials.
            body=failure.text or ''
            failures.append({'suite':row['suite'],'test':case.get('name'),
                'type':failure.get('type'),
                'causes':re.findall(r'Caused by: ([A-Za-z0-9_.$]+)',body),
                'frames':re.findall(r'(?m)^\s*at (com\.grash\.[A-Za-z0-9_.$<>]+\([^\r\n]*\))',body)[:8]})
xml_totals=totals.copy()
log_path=Path(a.log)
log=log_path.read_text(encoding='utf-8-sig',errors='replace')
log=re.sub(r'\x1b\[[0-?]*[ -/]*[@-~]','',log)  # Maven/Jansi on Linux runners
pattern=r'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)'
final=re.findall(r'^\[(?:INFO|ERROR)\] '+pattern+r'\s*$',log,re.MULTILINE)
class_rows=re.findall(pattern+r', Time elapsed: [^\r\n]+ -- in [^\r\n]+',log)
if len(final)!=1: problems.append('Expected exactly one Maven final result')
if len(final)==1:
    totals=dict(zip(totals,map(int,final[0])))
    class_sum=[sum(int(row[i]) for row in class_rows) for i in range(4)]
    if class_sum!=list(totals.values()): problems.append('Maven suite/final counts differ')
if not log_path.exists() or log_path.stat().st_mtime < start or '[INFO] BUILD SUCCESS' not in log:
    problems.append('Stale, incomplete or failed Maven build')
passed=totals['tests']-totals['failures']-totals['errors']-totals['skipped']
ok=(not problems and totals['tests']>=a.min_tests and not totals['failures'] and not totals['errors'] and (not a.require_no_skips or not totals['skipped']))
result={'startedAt':a.since,'recordedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'totals':totals,'xmlTotals':xml_totals,'countSource':'Maven final result cross-checked with all console suite rows; nested JUnit XML can omit parent-class cases','passed':passed,'result':'PASS' if ok else 'FAIL','suites':suites,'problems':problems,'failedCases':failures,'diagnosticFlags':{'mysqlTriggerBinlogPrivilege': 'log_bin_trust_function_creators' in log}}
Path(a.output).write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'totals':totals,'xmlTotals':xml_totals,'countSource':'Maven final result cross-checked with all console suite rows; nested JUnit XML can omit parent-class cases','passed':passed,'result':result['result']}))
raise SystemExit(0 if ok else 1)
