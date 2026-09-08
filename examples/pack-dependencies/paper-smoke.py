import os, pathlib, shutil, tempfile, subprocess, threading, queue, time
repo=pathlib.Path(__file__).resolve().parents[2]
java=str(pathlib.Path(os.environ['JAVA_HOME'])/'bin/java') if os.environ.get('JAVA_HOME') else 'java'
root=pathlib.Path(tempfile.mkdtemp(prefix='katton-paper-smoke-'))
(root/'plugins').mkdir()
shutil.copy2(repo/'paper/run/eula.txt',root/'eula.txt')
shutil.copy2(repo/'build/paper-katton-0.5.0-build1+mc26.1.2.jar',root/'plugins/katton.jar')
(root/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=true\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\nlevel-name=world\nmax-players=1\n')
packs=root/'world/kattonpacks'
packs.mkdir(parents=True)
for folder in ('shared','consumer'):
 shutil.copytree(repo/'examples/pack-dependencies'/folder,packs/folder)
consumer=packs/'consumer/Consumer.kt'
base=consumer.read_text().replace('Katton dependency smoke:', 'SMOKE_V1:')
# A real managed Paper callback must be restored without replaying its entrypoint.
base=base.replace('fun ready() {', '''fun ready() {
    top.katton.api.event.managed.registerEvent<org.bukkit.event.server.ServerCommandEvent> { event ->
        if (event.command == "smoke_ping") println("SMOKE_HANDLER_V1")
    }''')
consumer.write_text(base)
cp=[repo/'paper/run/versions/26.1.2/paper-26.1.2.jar']+sorted((repo/'paper/run/libraries').rglob('*.jar'))
log=root/'smoke.log'
q=queue.Queue(); lines=[]
p=subprocess.Popen([java,'-Xms256m','-Xmx1G','--enable-native-access=ALL-UNNAMED','-cp',':'.join(map(str,cp)),'org.bukkit.craftbukkit.Main','--nogui'],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
def read():
 with log.open('w') as out:
  for line in p.stdout:
   out.write(line);out.flush();lines.append(line);q.put(line)
threading.Thread(target=read,daemon=True).start()
def wait(marker, timeout=180):
 deadline=time.monotonic()+timeout
 while time.monotonic()<deadline:
  if p.poll() is not None: raise RuntimeError('Server exited before '+marker)
  try: line=q.get(timeout=1)
  except queue.Empty: continue
  if marker in line: print(line.strip(),flush=True); return
 raise TimeoutError(marker)
def command(cmd):
 p.stdin.write(cmd+'\n');p.stdin.flush()
print('SMOKE_DIRECTORY='+str(root),flush=True)
try:
 wait('For help, type "help"',300)
 command('list');wait('There are 0',5)
 wait('SMOKE_V1: shared counter = 1',300)
 command('smoke_ping');wait('SMOKE_HANDLER_V1')
 v2=base.replace('SMOKE_V1','SMOKE_V2').replace('SMOKE_HANDLER_V1','SMOKE_HANDLER_V2')
 consumer.write_text(v2)
 command('katton reload');wait('SMOKE_V2: shared counter = 2')
 command('smoke_ping');wait('SMOKE_HANDLER_V2')
 consumer.write_text(v2.replace('SMOKE_V2','SMOKE_FAIL').replace('SMOKE_HANDLER_V2','SMOKE_HANDLER_FAIL').replace('\n}', '\n    error("SMOKE_ENTRY_FAILURE")\n}'))
 command('katton reload');wait('Rejected pack transaction')
 command('smoke_ping');wait('SMOKE_HANDLER_V2')
 consumer.write_text(v2+'\n// successful new consumer revision\n')
 shared=packs/'shared/Shared.kt';shared.write_text(shared.read_text()+'\n// new dependency generation\n')
 command('katton reload');wait('SMOKE_V2: shared counter = 1')
 command('smoke_ping');wait('SMOKE_HANDLER_V2')
 assert sum('SMOKE_HANDLER_V1' in line for line in lines) == 1
 assert sum('SMOKE_HANDLER_V2' in line for line in lines) == 3
 assert not any('SMOKE_HANDLER_FAIL' in line for line in lines)
 print('SMOKE_PASSED: dependency state sharing, dependency replacement, and managed callback rollback',flush=True)
finally:
 if p.poll() is None:
  command('stop')
  try:p.wait(timeout=45)
  except subprocess.TimeoutExpired:p.terminate();p.wait(timeout=15)
 print('SMOKE_LOG='+str(log),flush=True)
