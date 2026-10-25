from pathlib import Path
import os,platform,subprocess,re,time,json,statistics
root=Path.cwd();binary=root/'target/hosted-binaries';out=root/'target/remaining-allocation-matrix';out.mkdir(parents=True,exist_ok=True)
assert platform.machine()=='arm64',platform.machine()
(out/'metadata.json').write_text(json.dumps({'platform':platform.platform(),'model':subprocess.check_output(['sysctl','-n','hw.model'],text=True).strip(),'binaries_from_run':40190222452,'candidate':'b482627af'},indent=2))
for name in ('baseline','after'):(binary/name/'benchmark').chmod(0o755)
rows=[]
for scenario in ('medium','large','blocks'):
 for workers in (1,16):
  for repeat in range(3):
   for name in (('baseline','after') if repeat%2==0 else ('after','baseline')):
    env=os.environ.copy();env.update(GC_INITIAL_HEAP_SIZE='256M',GC_MAXIMUM_HEAP_SIZE='256M' if workers==1 else '8G',SCALANATIVE_GC_LOG_LEVEL='warn');env.pop('GC_LOG_FILE',None)
    t=time.monotonic();p=subprocess.run(['/usr/bin/time','-l',str(binary/name/'benchmark'),scenario,str(workers),'5'],env=env,capture_output=True,text=True,timeout=60);wall=time.monotonic()-t
    key=f'{scenario}-{workers}-{repeat}-{name}';(out/(key+'.stdout')).write_text(p.stdout);(out/(key+'.stderr')).write_text(p.stderr);assert p.returncode==0,p.stderr
    f=dict(re.findall(r'(\w+)=([^\s]+)',p.stdout));elapsed=float(f['elapsed']);assert -0.1<=wall-elapsed<=1,(wall,elapsed)
    r=dict(scenario=scenario,workers=workers,repeat=repeat,variant=name,rate=int(f['allocations'])/elapsed,allocations=int(f['allocations']),wall=wall,elapsed=elapsed,collections=int(f['collections']),gc_ms=int(f['gc_ms']),heap_bytes=int(f['heap_bytes']),rss=int(re.search(r'(\d+)\s+maximum resident set size',p.stderr)[1]))
    rows.append(r);(out/'results.json').write_text(json.dumps(rows,indent=2));print(r,flush=True)
for scenario in ('medium','large','blocks'):
 for workers in (1,16):
  changes=[100*(next(r['rate'] for r in rows if r['scenario']==scenario and r['workers']==workers and r['repeat']==i and r['variant']=='after')/next(r['rate'] for r in rows if r['scenario']==scenario and r['workers']==workers and r['repeat']==i and r['variant']=='baseline')-1) for i in range(3)]
  print('SUMMARY',scenario,workers,'median',statistics.median(changes),'pairs',changes,flush=True)
