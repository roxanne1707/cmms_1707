from pathlib import Path
import subprocess,secrets,json,base64,argparse
parser=argparse.ArgumentParser(description="Create an EMPTY isolated MySQL lab on loopback :23306. Refuses existing databases or credential files.")
parser.add_argument("--mysql",required=True)
parser.add_argument("--client-config",required=True,help="Private MySQL administrator client option file; never commit it")
args=parser.parse_args()
dest=Path(__file__).resolve().parents[1]/'local-runtime/mysql-maintenance'
cmd=[args.mysql,'--defaults-extra-file='+str(Path(args.client_config).resolve()),'--protocol=TCP','--host=127.0.0.1','--port=23306','--batch','--skip-column-names']
def sql(s):
 p=subprocess.run(cmd,input=s,text=True,capture_output=True)
 if p.returncode:raise RuntimeError(p.stderr)
 return p.stdout.strip()
assert sql('SELECT @@port')=='23306'
assert not (dest/'access.json').exists(),'Never reset existing credentials'
assert sql("SELECT count(*) FROM information_schema.schemata WHERE schema_name='cmms_mysql_lab'")=='0','Never reuse or overwrite an existing database'
creds={'runtimeUser':'cmms_mysql_app','runtimePassword':secrets.token_hex(24),'migrationUser':'cmms_mysql_migrate','migrationPassword':secrets.token_hex(24),'adminEmail':'mysql-admin@example.invalid','adminPassword':'Cmms!'+secrets.token_hex(18),'jwtSecret':base64.b64encode(secrets.token_bytes(64)).decode(),'metricsPassword':secrets.token_hex(24)}
sql(f"""CREATE DATABASE cmms_mysql_lab CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
CREATE USER 'cmms_mysql_app'@'127.0.0.1' IDENTIFIED BY '{creds['runtimePassword']}';
CREATE USER 'cmms_mysql_migrate'@'127.0.0.1' IDENTIFIED BY '{creds['migrationPassword']}';
GRANT SELECT,INSERT,UPDATE,DELETE ON cmms_mysql_lab.* TO 'cmms_mysql_app'@'127.0.0.1';
GRANT ALL PRIVILEGES ON cmms_mysql_lab.* TO 'cmms_mysql_migrate'@'127.0.0.1';""")
dest.mkdir(parents=True,exist_ok=True)
(dest/'access.json').write_text(json.dumps(creds,indent=2),encoding='utf-8')
print('Created empty cmms_mysql_lab and separate DML/migration accounts; credentials saved in ignored local-runtime')
