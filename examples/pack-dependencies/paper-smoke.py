import argparse, os, pathlib, shutil, tempfile, subprocess, threading, time, zipfile
repo=pathlib.Path(__file__).resolve().parents[2]
mod_version=next(line.split('=',1)[1].strip() for line in (repo/'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
parser=argparse.ArgumentParser(description='Real Paper/Folia dependency and rollback smoke test (Python 3).')
parser.add_argument('--server-jar', type=pathlib.Path, required=True)
parser.add_argument('--plugin-jar', type=pathlib.Path, default=repo/f'build/paper-katton-{mod_version}+mc26.1.2.jar')
parser.add_argument('--eula-file', type=pathlib.Path, required=True)
parser.add_argument('--output', type=pathlib.Path)
parser.add_argument('--vanilla-jar', type=pathlib.Path, help='Optional predownloaded Mojang 26.1.2 server JAR')
parser.add_argument('--zip', action='store_true', help='Run the same reload scenarios using ZIP packs')
args=parser.parse_args()
for path in (args.server_jar,args.plugin_jar,args.eula_file):
 if not path.is_file(): parser.error('Missing input: '+str(path))
if 'eula=true' not in args.eula_file.read_text().replace(' ','').lower().splitlines():
 parser.error('--eula-file must contain eula=true')
java=str(pathlib.Path(os.environ['JAVA_HOME'])/('bin/java.exe' if os.name=='nt' else 'bin/java')) if os.environ.get('JAVA_HOME') else 'java'
root=args.output.resolve() if args.output else pathlib.Path(tempfile.mkdtemp(prefix='katton-server-smoke-'))
root.mkdir(parents=True,exist_ok=True)
if any(root.iterdir()): parser.error('--output must be empty')
(root/'plugins').mkdir()
shutil.copy2(args.eula_file,root/'eula.txt')
shutil.copy2(args.plugin_jar,root/'plugins/katton.jar')
if args.vanilla_jar:
 (root/'cache').mkdir()
 shutil.copy2(args.vanilla_jar,root/'cache/mojang_26.1.2.jar')
(root/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=true\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\nlevel-name=world\nmax-players=1\n')
packs=root/'world/kattonpacks'
packs.mkdir(parents=True)
sources=root/'fixture-sources' if args.zip else packs
for folder in ('shared','consumer'):
 shutil.copytree(repo/'examples/pack-dependencies'/folder,sources/folder)
consumer=sources/'consumer/Consumer.kt'
def publish():
 if not args.zip: return
 for folder in ('shared','consumer'):
  staging=root/(folder+'.zip.tmp')
  with zipfile.ZipFile(staging,'w') as archive:
   for file in (sources/folder).rglob('*'):
    if file.is_file(): archive.write(file,file.relative_to(sources/folder).as_posix())
  staging.replace(packs/(folder+'.zip'))
base=consumer.read_text().replace('Katton dependency smoke:', 'SMOKE_V1:')
# A real managed Paper callback must be restored without replaying its entrypoint.
base=base.replace('fun ready() {', '''fun ready() {
    top.katton.api.event.managed.registerEvent<org.bukkit.event.server.ServerCommandEvent> { event ->
        if (event.command == "smoke_ping") println("SMOKE_HANDLER_V1")
    }
    top.katton.paper.scheduleGlobal(delayTicks = 2) { println("SMOKE_SCHEDULE_V1") }''')
consumer.write_text(base)
publish()
log=root/'smoke.log'
lines=[]; condition=threading.Condition()
p=subprocess.Popen([java,'-Xms256m','-Xmx2G','--enable-native-access=ALL-UNNAMED','-jar',str(args.server_jar.resolve()),'--nogui'],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace',bufsize=1)
def read():
 with log.open('w',encoding='utf-8') as out:
  for line in p.stdout:
   out.write(line);out.flush()
   with condition:
    lines.append(line);condition.notify_all()
reader=threading.Thread(target=read,daemon=True);reader.start()
def wait(marker, timeout=300, start=0):
 deadline=time.monotonic()+timeout
 with condition:
  while time.monotonic()<deadline:
   match=next((line for line in lines[start:] if marker in line),None)
   if match: print(match.strip(),flush=True);return
   if p.poll() is not None: raise RuntimeError('Server exited before '+marker)
   condition.wait(timeout=1)
 raise TimeoutError(marker)
def command(cmd):
 with condition: start=len(lines)
 p.stdin.write(cmd+'\n');p.stdin.flush()
 return start
print('SMOKE_DIRECTORY='+str(root),flush=True)
try:
 wait('For help, type "help"',600)
 wait('There are 0',10,start=command('list'))
 wait('SMOKE_V1: shared counter = 1',300)
 wait('SMOKE_SCHEDULE_V1')
 wait('SMOKE_HANDLER_V1',start=command('smoke_ping'))
 v2=base.replace('SMOKE_V1','SMOKE_V2').replace('SMOKE_HANDLER_V1','SMOKE_HANDLER_V2').replace('SMOKE_SCHEDULE_V1','SMOKE_SCHEDULE_V2')
 consumer.write_text(v2)
 publish()
 start=command('katton reload');wait('SMOKE_V2: shared counter = 2',start=start)
 wait('SMOKE_SCHEDULE_V2',start=start)
 wait('SMOKE_HANDLER_V2',start=command('smoke_ping'))
 consumer.write_text(v2.replace('SMOKE_V2','SMOKE_FAIL').replace('SMOKE_HANDLER_V2','SMOKE_HANDLER_FAIL').replace('SMOKE_SCHEDULE_V2','SMOKE_SCHEDULE_FAIL').replace('\n}', '\n    error("SMOKE_ENTRY_FAILURE")\n}'))
 publish()
 wait('Rejected pack transaction',start=command('katton reload'))
 wait('SMOKE_HANDLER_V2',start=command('smoke_ping'))
 consumer.write_text(v2+'\n// successful new consumer revision\n')
 shared=sources/'shared/Shared.kt';shared.write_text(shared.read_text()+'\n// new dependency generation\n')
 publish()
 start=command('katton reload');wait('SMOKE_V2: shared counter = 1',start=start)
 wait('SMOKE_SCHEDULE_V2',start=start)
 wait('SMOKE_HANDLER_V2',start=command('smoke_ping'))
 assert sum('SMOKE_HANDLER_V1' in line for line in lines) == 1
 assert sum('SMOKE_HANDLER_V2' in line for line in lines) == 3
 assert not any('SMOKE_HANDLER_FAIL' in line or 'SMOKE_SCHEDULE_FAIL' in line for line in lines)
 assert sum('SMOKE_V2: shared counter =' in line for line in lines)==2, 'Rollback replayed an old entrypoint'
 print('SMOKE_PASSED: dependency state sharing, dependency replacement, managed callback rollback, global scheduling',flush=True)
finally:
 if p.poll() is None:
  command('stop')
  try:p.wait(timeout=45)
  except subprocess.TimeoutExpired:p.terminate();p.wait(timeout=15)
 reader.join(timeout=5)
 print('SMOKE_LOG='+str(log),flush=True)
