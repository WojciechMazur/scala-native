from pathlib import Path
import concurrent.futures,hashlib,io,json,os,platform,re,shlex,shutil,statistics,subprocess,tarfile,time
root=Path.cwd();out=root/'target/isolated-allocation-benchmark';out.mkdir(parents=True,exist_ok=True)
work=root/'target/out/native0.5/scala-3.8.4/sandbox/native'
info=shlex.split((work/'llvmLinkInfo').read_text());clang=shutil.which('clang')
metadata={'platform':platform.platform(),'machine':platform.machine(),'clang':subprocess.check_output([clang,'--version'],text=True),'app_sha256':hashlib.sha256((root/'scripted-tests/run/gc-concurrent-allocation/benchmark/AllocationBenchmark.scala').read_bytes()).hexdigest(),'candidate':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()}
(out/'metadata.json').write_text(json.dumps(metadata,indent=2))
refs={'baseline':'1b8a98c16','before':'8cce9b12b','after':'HEAD'}
for variant,ref in refs.items():
 source=out/variant/'scala-native';shutil.copytree(root/'nativelib/src/main/resources/scala-native',source,dirs_exist_ok=True);shutil.rmtree(source/'gc')
 prefix='nativelib/src/main/resources/scala-native/'
 archive=subprocess.check_output(['git','archive',ref,prefix+'gc'])
 with tarfile.open(fileobj=io.BytesIO(archive)) as tar:
  for member in tar.getmembers():
   if member.isfile():
    dest=source/member.name[len(prefix):];dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(tar.extractfile(member).read())
 flags=['-std=gnu11','-fexceptions','-funwind-tables','-gdwarf-4','-fvisibility=hidden','-O2','-fomit-frame-pointer','-DNDEBUG','-D_LIBUNWIND_IS_NATIVE_ONLY','-DSCALANATIVE_GC_IMMIX','-DSCALANATIVE_MULTITHREADING_ENABLED','-DSCALANATIVE_GC_USE_YIELDPOINT_TRAPS']+['-I'+str(source/p) for p in ['.','gc','gc/immix','platform/posix/libunwind']]
 args=info.copy();jobs=[];objects=out/variant/'objects';objects.mkdir()
 for i,arg in enumerate(args):
  if '/scala-native/gc/' in arg and arg.endswith('.c.o'):
   rel=arg.rsplit('/scala-native/',1)[1][:-2];dest=objects/(rel.replace('/','_')+'.o');jobs.append([clang,'-c',str(source/rel),'-o',str(dest),*flags]);args[i]=str(dest)
 with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
  for result in pool.map(lambda cmd:subprocess.run(cmd,check=True,capture_output=True,text=True),jobs):
   if result.stderr:print(result.stderr,flush=True)
 args[args.index('-o')+1]=str(out/variant/'benchmark');subprocess.run([clang,*args],cwd=work,check=True)
 print('Rebuilt',variant,ref,len(jobs),'GC objects',flush=True)
rows=[]
for scenario in ('objects','small'):
 for workers in (1,16):
  names=list(refs)
  for name in names:
   env=os.environ.copy();env.update(GC_INITIAL_HEAP_SIZE='256M',GC_MAXIMUM_HEAP_SIZE='256M' if workers==1 else '8G',SCALANATIVE_GC_LOG_LEVEL='warn');env.pop('GC_LOG_FILE',None)
   subprocess.run([str(out/name/'benchmark'),scenario,str(workers),'1'],env=env,check=True,capture_output=True,timeout=30)
  for repeat in range(5):
   variants=names[repeat%3:]+names[:repeat%3]
   for name in variants:
    env=os.environ.copy();env.update(GC_INITIAL_HEAP_SIZE='256M',GC_MAXIMUM_HEAP_SIZE='256M' if workers==1 else '8G',SCALANATIVE_GC_LOG_LEVEL='warn');env.pop('GC_LOG_FILE',None)
    started=time.monotonic();p=subprocess.run(['/usr/bin/time','-l',str(out/name/'benchmark'),scenario,str(workers),'8'],env=env,capture_output=True,text=True,timeout=60);wall=time.monotonic()-started
    key=f'{scenario}-{workers}-{repeat}-{name}';(out/(key+'.stdout')).write_text(p.stdout);(out/(key+'.stderr')).write_text(p.stderr);assert p.returncode==0,p.stderr
    f=dict(re.findall(r'(\w+)=([^\s]+)',p.stdout));elapsed=float(f['elapsed']);assert -0.1<=wall-elapsed<=1.0,(wall,elapsed)
    r=dict(scenario=scenario,workers=workers,repeat=repeat,variant=name,rate=int(f['allocations'])/elapsed,allocations=int(f['allocations']),elapsed=elapsed,wall=wall,collections=int(f['collections']),gc_ms=int(f['gc_ms']),heap_bytes=int(f['heap_bytes']),rss=int(re.search(r'(\d+)\s+maximum resident set size',p.stderr)[1]))
    rows.append(r);(out/'results.json').write_text(json.dumps(rows,indent=2));print(r,flush=True)
for scenario in ('objects','small'):
 for workers in (1,16):
  for comparator in ('baseline','before'):
   changes=[100*(next(r['rate'] for r in rows if r['scenario']==scenario and r['workers']==workers and r['repeat']==i and r['variant']=='after')/next(r['rate'] for r in rows if r['scenario']==scenario and r['workers']==workers and r['repeat']==i and r['variant']==comparator)-1) for i in range(5)]
   print('SUMMARY',scenario,workers,'after vs',comparator,'median',statistics.median(changes),'pairs',changes,flush=True)
