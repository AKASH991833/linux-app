import os, re, subprocess, sys, time
import xml.etree.ElementTree as ET

API = int(sys.argv[1])
OUT = os.environ.get('OUT_DIR') or os.path.join(os.environ.get('GITHUB_WORKSPACE', '/tmp'), 'out')
os.makedirs(OUT, exist_ok=True)
PKG = 'com.akash.linuxapp'
results = []

def run(cmd):
    return subprocess.run(cmd, capture_output=True)

def check(name, cond, extra=''):
    results.append((name, bool(cond)))
    print(('PASS' if cond else 'FAIL') + ': ' + name + (' | ' + str(extra) if extra else ''), flush=True)

def finish(code):
    with open(os.path.join(OUT, 'results.txt'), 'w') as f:
        for name, ok in results:
            f.write(('PASS' if ok else 'FAIL') + ': ' + name + '\n')
    r = run(['adb', 'logcat', '-d'])
    with open(os.path.join(OUT, 'logcat.txt'), 'w') as f:
        f.write(r.stdout.decode('utf-8', 'ignore'))
    failed = [n for n, ok in results if not ok]
    print('==== %d/%d checks passed ====' % (len(results) - len(failed), len(results)), flush=True)
    sys.exit(code)

def die(msg):
    print('ABORT: ' + msg, flush=True)
    shot('die')
    finish(1)

def shot(name):
    p = os.path.join(OUT, name + '.png')
    r = run(['adb', 'exec-out', 'screencap', '-p'])
    if r.stdout.startswith(b'\x89PNG'):
        with open(p, 'wb') as f:
            f.write(r.stdout)
        return p
    run(['adb', 'shell', 'screencap', '-p', '/sdcard/' + name + '.png'])
    run(['adb', 'pull', '/sdcard/' + name + '.png', p])
    return p

def dump():
    xml = ''
    for _ in range(4):
        run(['adb', 'shell', 'uiautomator', 'dump', '/sdcard/ui.xml'])
        time.sleep(0.5)
        r = run(['adb', 'shell', 'cat', '/sdcard/ui.xml'])
        xml = r.stdout.decode('utf-8', 'ignore')
        if '<hierarchy' in xml:
            break
        time.sleep(1)
    return xml

def find_all(xml, text=None, contains=None, clazz=None, clickable=None, regex=None):
    out = []
    try:
        root = ET.fromstring(xml)
    except Exception:
        return out
    if root is None:
        return out
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
    run(['adb', 'shell', 'input', 'tap', str(x), str(y)])

def wait_node(timeout=30, **kw):
    end = time.time() + timeout
    while time.time() < end:
        xml = dump()
        anr = find_all(xml, regex=r"isn't responding")
        if anr:
            w = find_all(xml, text='Wait')
            if w:
                print('ANR dialog seen - tapping Wait', flush=True)
                tap_node(w[0])
                time.sleep(3)
                end += 30
                continue
        nodes = find_all(xml, **kw)
        if nodes:
            return nodes, xml
        time.sleep(1)
    return [], xml

def main():
    r = run(['adb', 'install', '-r', 'app/build/outputs/apk/debug/app-debug.apk'])
    out = r.stdout.decode('utf-8', 'ignore') + r.stderr.decode('utf-8', 'ignore')
    check('apk installs', 'Success' in out, out.strip().splitlines()[-1] if out.strip() else '')
    run(['adb', 'shell', 'monkey', '-p', PKG, '-c', 'android.intent.category.LAUNCHER', '1'])
    nodes, xml = wait_node(45, contains='Linux App')
    check('home screen shows app title', bool(nodes))
    if not nodes:
        die('app did not open')
    check('daily challenge card present', bool(find_all(xml, contains='Daily Challenge')))
    check('chapter list present', bool(find_all(xml, contains='Linux History')))
    shot('01_home')
    chap = find_all(xml, contains='Linux History')
    tap_node(chap[0])
    nodes, xml = wait_node(20, contains='Start Quiz')
    check('chapter page opens with Start Quiz', bool(nodes))
    check('lessons listed', bool(find_all(xml, contains='short lessons')))
    shot('02_chapter')
    les = find_all(xml, contains='What is Linux, really?')
    if les:
        tap_node(les[0])
        nodes, xml = wait_node(20, contains='Lesson 1 of')
        check('lesson opens with body', bool(nodes) and bool(find_all(xml, contains='KERNEL')))
        check('diagram rendered', bool(find_all(xml, contains='HARDWARE')))
        check('real-life scenario shown', bool(find_all(xml, contains="Where you'd use it")))
        shot('03_lesson')
        # The lesson is scrollable. Drive the quick check and verify progress is earned only on a correct answer.
        nodes = []
        for _ in range(5):
            run(['adb', 'shell', 'input', 'swipe', '480', '1600', '480', '250', '450'])
            nodes, xml = wait_node(2, contains='Quick check')
            if nodes and find_all(xml, text='Linux kernel', clazz='Button'):
                break
        check('lesson quick check visible', bool(nodes))
        shot('03b_quick_check')
        q_answer = find_all(xml, text='Linux kernel', clazz='Button')
        if q_answer:
            tap_node(q_answer[0])
            nodes, xml = wait_node(5, contains='You got it!')
            if not nodes:
                run(['adb', 'shell', 'input', 'swipe', '480', '1450', '480', '450', '450'])
                nodes, xml = wait_node(8, contains='You got it!')
            check('correct quick check gives feedback', bool(nodes))
            shot('03c_check_passed')
        else:
            check('correct quick check gives feedback', False, 'answer option not found')
        run(['adb', 'shell', 'input', 'keyevent', '4'])
        time.sleep(1)
        nodes, xml = wait_node(15, contains='Start Quiz')
        if not nodes:
            chap = find_all(xml, contains='Linux History')
            if chap:
                tap_node(chap[0])
                nodes, xml = wait_node(15, contains='Start Quiz')
    start = find_all(xml, contains='Start Quiz')
    if not start:
        die('Start Quiz button not found')
    tap_node(start[0])
    nodes, xml = wait_node(20, contains='Question 1 of')
    check('quiz opens at question 1', bool(nodes))
    opts = [n for n in find_all(xml, clazz='Button') if (n.get('text') or '').strip() and n.get('text') not in ('Next', 'See results')]
    check('4 options shown', len(opts) == 4, 'found %d' % len(opts))
    shot('04_question')
    if not opts:
        die('no option buttons')
    tap_node(opts[0])
    nodes, xml = wait_node(15, regex=r'^(Next|See results)$')
    check('answer feedback + explanation shown', bool(nodes) and bool(find_all(xml, contains='Correct') + find_all(xml, contains='Not quite')))
    shot('05_feedback')
    nxt = find_all(xml, regex=r'^(Next|See results)$')
    if nxt:
        tap_node(nxt[0])
        nodes, xml = wait_node(15, contains='Question 2 of')
        check('next advances to question 2', bool(nodes))
        shot('06_question2')
    run(['adb', 'shell', 'input', 'keyevent', '4'])
    time.sleep(1)
    xml = dump()
    check('back returns home', bool(find_all(xml, contains='Linux App')))
    r = run(['adb', 'logcat', '-d'])
    logs = r.stdout.decode('utf-8', 'ignore')
    fatal = [l for l in logs.splitlines() if 'FATAL EXCEPTION' in l and PKG in l]
    check('no fatal crash in logcat', not fatal, fatal[0] if fatal else '')
    failed = [n for n, ok in results if not ok]
    finish(1 if failed else 0)

main()
