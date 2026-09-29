import os,re,subprocess,time,xml.etree.ElementTree as ET
OUT=os.environ.get('OUT_DIR','out-diagrams');os.makedirs(OUT,exist_ok=True)
def adb(*args,timeout=30):return subprocess.run(['adb',*args],capture_output=True,timeout=timeout).stdout
def dump():
 adb('shell','uiautomator','dump','/sdcard/ui.xml');return adb('shell','cat','/sdcard/ui.xml').decode('utf-8','ignore')
def find(xml,label):
 try: return [n for n in ET.fromstring(xml).iter('node') if label in (n.get('text') or '')]
 except: return []
def tap(n):
 x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')));adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
def until(label,tries=16):
 for _ in range(tries):
  x=dump()
  if find(x,label):return x
  wait=find(x,'Wait')
  if wait:tap(wait[0])
  time.sleep(1)
 raise RuntimeError('Not found: '+label)
def swipe():adb('shell','input','swipe','480','1550','480','450','450')
def shot(n):open(os.path.join(OUT,n+'.png'),'wb').write(adb('exec-out','screencap','-p'))
print(adb('install','-r','app/build/outputs/apk/debug/app-debug.apk',timeout=120).decode(),flush=True)
adb('shell','pm','clear','com.akash.linuxapp');adb('shell','am','start','-n','com.akash.linuxapp/.MainActivity');x=until('Welcome to');tap(find(x,'Get Started')[0]);x=until('Continue Learning');
for _ in range(8):
 x=dump()
 if find(x,'Diagrams'):break
 swipe()
else:raise RuntimeError('Diagrams card not found on Home')
shot('01_home_diagrams');tap(find(x,'Diagrams')[0]);x=until('See how Linux works');shot('02_diagram_list')
for name,title in [('filesystem','Filesystem hierarchy'),('boot','Linux boot path'),('network','Network request')]:
 x=until(title);tap(find(x,title)[0]);x=until('Read concept source');shot('03_'+name+'_top');
 for _ in range(4):swipe()
 shot('04_'+name+'_bottom');x=until('Back to Diagrams');tap(find(x,'Back to Diagrams')[0]);until('See how Linux works')
print('PASS: three diagram detail screens opened and captured top/bottom',flush=True)
