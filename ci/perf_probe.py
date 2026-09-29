import json, re, subprocess, time, statistics
P='com.akash.linuxapp'
def adb(*args): return subprocess.run(['adb',*args],capture_output=True,text=True,timeout=30).stdout
results={}
adb('shell','pm','clear',P)
for i in range(5):
    adb('shell','am','force-stop',P)
    out=adb('shell','am','start','-W','-n',P+'/.MainActivity')
    m=re.search(r'TotalTime:\s*(\d+)',out)
    results.setdefault('cold_activity_ms',[]).append(int(m.group(1)) if m else out.strip())
    time.sleep(3)
print(json.dumps(results,indent=2))
