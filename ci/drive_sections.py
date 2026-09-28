import os, re, subprocess, sys, time
import xml.etree.ElementTree as ET

API = int(sys.argv[1])
OUT = os.environ.get('OUT_DIR') or os.path.join(os.environ.get('GITHUB_WORKSPACE', '/tmp'), 'out-sections')
os.makedirs(OUT, exist_ok=True)
PKG = 'com.akash.linuxapp'
results = []

def run(cmd, timeout=20):
    try:
        return subprocess.run(cmd, capture_output=True, timeout=timeout)
    except subprocess.TimeoutExpired as e:
        stdout = e.stdout or b''
        stderr = e.stderr or b''
        return subprocess.CompletedProcess(cmd, 124, stdout=stdout, stderr=stderr + b' TIMEOUT')

def check(name, cond, extra=''):
    results.append((name, bool(cond)))
    print(('PASS' if cond else 'FAIL') + ': ' + name + (' | ' + str(extra) if extra else ''), flush=True)

def shot(name):
    p = os.path.join(OUT, name + '.png')
    r = run(['adb', 'exec-out', 'screencap', '-p'], timeout=15)
    if r.stdout.startswith(b'\x89PNG'):
        with open(p, 'wb') as f:
            f.write(r.stdout)
        return p
    run(['adb', 'shell', 'screencap', '-p', '/sdcard/' + name + '.png'], timeout=15)
    run(['adb', 'pull', '/sdcard/' + name + '.png', p], timeout=15)
    return p if os.path.exists(p) else None

def dump():
    xml = ''
    for _ in range(3):
        run(['adb', 'shell', 'uiautomator', 'dump', '/sdcard/ui.xml'], timeout=15)
        time.sleep(0.5)
        r = run(['adb', 'shell', 'cat', '/sdcard/ui.xml'], timeout=15)
        xml = r.stdout.decode('utf-8', 'ignore')
        if '<hierarchy' in xml:
            break
        time.sleep(1)
    return xml

def find_all(xml, text=None, contains=None, clazz=None, clickable=None, regex=None):
    try:
        root = ET.fromstring(xml)
    except Exception:
        return []
    out = []
    for n in root.iter('node'):
        t = n.get('text') or ''
        if text is not None and t != text:
            continue
        if contains is not None and contains not in t:
            continue
        if regex is not None and not re.search(regex, t):
            continue
        if clazz is not None and not (n.get('class') or '').endswith(clazz):
            continue
        if clickable is not None and (n.get('clickable') == 'true') != clickable:
            continue
        out.append(n)
    return out

def center(n):
    m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.get('bounds'))
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2

def tap_node(n):
    x, y = center(n)
    run(['adb', 'shell', 'input', 'tap', str(x), str(y)], timeout=10)

def tap_exact(xml, label, contains=False):
    nodes = find_all(xml, contains=label) if contains else find_all(xml, text=label)
    if nodes:
        tap_node(nodes[0])
        return True
    return False

def yrange(n):
    m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.get('bounds'))
    return int(m.group(2)), int(m.group(4))

def tap_open_near(xml, label):
    titles = find_all(xml, contains=label)
    if not titles:
        return False
    ty1, ty2 = yrange(titles[0])
    for b in find_all(xml, text='Open', clazz='Button'):
        by1, by2 = yrange(b)
        if by1 < ty2 and by2 > ty1:
            tap_node(b)
            return True
    tap_node(titles[0])
    return True

def wait_node(timeout=30, **kw):
    end = time.time() + timeout
    xml = ''
    while time.time() < end:
        xml = dump()
        anr = find_all(xml, regex=r"isn't responding")
        if anr:
            wait = find_all(xml, text='Wait')
            if wait:
                print('ANR dialog seen - tapping Wait', flush=True)
                tap_node(wait[0])
                time.sleep(3)
                end += 30
                continue
        nodes = find_all(xml, **kw)
        if nodes:
            return nodes, xml
        time.sleep(1)
    return [], xml

def finish():
    with open(os.path.join(OUT, 'results.txt'), 'w') as f:
        for name, ok in results:
            f.write(('PASS' if ok else 'FAIL') + ': ' + name + '\n')
    failed = [n for n, ok in results if not ok]
    print('==== %d/%d checks passed ====' % (len(results) - len(failed), len(results)), flush=True)
    sys.exit(1 if failed else 0)

def wait_home():
    nodes, xml = wait_node(45, contains='Continue Learning')
    if nodes:
        return nodes, xml
    welcome, welcome_xml = wait_node(5, contains='Welcome to')
    if welcome and tap_exact(welcome_xml, 'Get Started'):
        return wait_node(45, contains='Continue Learning')
    return nodes, xml

def restart_app():
    run(['adb', 'shell', 'am', 'force-stop', PKG], timeout=10)
    run(['adb', 'shell', 'am', 'start', '-n', PKG + '/.MainActivity'], timeout=10)
    return wait_home()

def open_interview_sections():
    nodes, xml = restart_app()
    if not nodes:
        return nodes, xml
    if not tap_exact(xml, 'Interview Questions'):
        return [], xml
    return wait_node(25, contains='Your Questions & PDFs')

r = run(['adb', 'install', '-r', 'app/build/outputs/apk/debug/app-debug.apk'], timeout=120)
out = r.stdout.decode('utf-8', 'ignore') + r.stderr.decode('utf-8', 'ignore')
check('apk installs', 'Success' in out, out.strip().splitlines()[-1] if out.strip() else '')
run(['adb', 'shell', 'am', 'start', '-n', PKG + '/.MainActivity'], timeout=10)
nodes, xml = wait_home()
check('home opens', bool(nodes))

nodes, xml = restart_app()
check('home opens for Commands', bool(nodes))
if tap_exact(xml, 'Commands'):
    nodes, xml = wait_node(25, contains='Learn Linux commands')
    check('Commands topics open', bool(nodes))
    shot('commands_topics')
    if tap_exact(xml, 'Navigation & Basics'):
        nodes, xml = wait_node(25, contains='COMMAND')
        check('Commands table opens', bool(nodes))
        shot('commands_table')
        # Record the exact accessibility hierarchy and each candidate so failures are diagnosable.
        open(os.path.join(OUT, 'command_table.xml'), 'w').write(xml)
        candidates = [n for n in find_all(xml, clazz='LinearLayout', clickable=True) if (n.get('content-desc') or '').startswith('ls.')]
        print('ls row candidates: '+repr([(n.get('bounds'),n.get('content-desc')) for n in candidates]),flush=True)
        if candidates:
            tap_node(candidates[0])
            nodes, xml = wait_node(8, contains='What it does')
            if not nodes:
                # If the center was obscured, tap the left half of the same visible row.
                a,b = yrange(candidates[0]); x,y = center(candidates[0])
                run(['adb','shell','input','tap',str(max(90,x//2)),str((a+b)//2)])
                nodes, xml = wait_node(8, contains='What it does')
            check('Command detail opens with flags', bool(nodes) and bool(find_all(xml, contains='Try this example')))
            open(os.path.join(OUT, 'command_detail.xml'), 'w').write(xml)
            shot('commands_ls_detail')
        else: check('Command detail opens with flags',False)
    else: check('Commands table opens',False)
else: check('Commands topics open',False)

nodes, xml = open_interview_sections()
check('interview sections open',bool(nodes))
open(os.path.join(OUT, 'interview_sections.xml'), 'w').write(xml)
print('PDF candidates: '+repr([(n.get('text'),n.get('bounds')) for n in find_all(xml, contains='200 Important Commands')]),flush=True)
if tap_open_near(xml, '200 Important Commands'):
    nodes, xml = wait_node(8, contains='all 200 rows in original order')
    if not nodes:
        # The whole card is clickable, not just its label.
        before=xml
        titles=find_all(before, contains='200 Important Commands')
        if titles:
            x,y=center(titles[0]); run(['adb','shell','input','tap',str(x),str(y)])
            nodes,xml=wait_node(8,contains='all 200 rows in original order')
    check('200 Important Commands opens',bool(nodes) and bool(find_all(xml,contains='#1')))
    open(os.path.join(OUT, 'commands_200.xml'), 'w').write(xml)
    shot('commands_200')
else: check('200 Important Commands opens',False)

finish()
