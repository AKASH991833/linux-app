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

PAGES = []

def scroll_down(n=1):
    for _ in range(n):
        run(['adb', 'shell', 'input', 'swipe', '480', '1600', '480', '500', '400'])
        time.sleep(0.7)

def scroll_up(n=1):
    for _ in range(n):
        run(['adb', 'shell', 'input', 'swipe', '480', '500', '480', '1600', '400'])
        time.sleep(0.7)

def find_scrolled(max_swipes=16, **kw):
    """Scroll down the current screen until nodes match; records every page seen."""
    nodes, xml = wait_node(2, **kw)
    PAGES.append(xml)
    swipes = 0
    while not nodes and swipes < max_swipes:
        scroll_down()
        nodes, xml = wait_node(2, **kw)
        PAGES.append(xml)
        swipes += 1
    return nodes, xml

def main():
    r = run(['adb', 'install', '-r', 'app/build/outputs/apk/debug/app-debug.apk'])
    out = r.stdout.decode('utf-8', 'ignore') + r.stderr.decode('utf-8', 'ignore')
    check('apk installs', 'Success' in out, out.strip().splitlines()[-1] if out.strip() else '')
    run(['adb', 'shell', 'monkey', '-p', PKG, '-c', 'android.intent.category.LAUNCHER', '1'])
    nodes, xml = wait_node(45, contains='Linux App')
    PAGES.append(xml)
    check('home screen shows app title', bool(nodes))
    if not nodes:
        die('app did not open')
    check('daily challenge card present', bool(find_all(xml, contains='Daily Challenge')))
    check('home shows v1.2 content marker', bool(find_all(xml, contains='v1.2.0')) and bool(find_all(xml, contains='328 interview Q&A')))
    shot('01_home')

    # Quiz section: only quizzes, one card per topic area
    nodes, xml = find_scrolled(contains='Quiz - Topic-wise')
    check('quiz section header present', bool(nodes))
    check('beginner and intermediate modes present',
          bool(find_all(xml, text='Beginner')) and bool(find_all(xml, text='Intermediate')))
    check('no interview timer mode button', not find_all(xml, text='Interview'))
    shot('02_quiz_section')

    nodes, xml = find_scrolled(contains='Linux History & Foundations Quiz')
    check('topic quiz card present', bool(nodes))
    if nodes:
        tap_node(nodes[0])
        nodes, xml = wait_node(20, contains='Question 1 of')
        check('topic quiz opens at question 1', bool(nodes))
        check('topic quiz shows its area name', bool(find_all(xml, contains='Question 1 of 10 • Linux History & Foundations')))
        qspeak = find_all(xml, text='🔊 Speak question', clazz='Button')
        check('quiz speak button present', bool(qspeak))
        if qspeak:
            tap_node(qspeak[0])
            time.sleep(1.2)
            xml = dump()
            check('quiz speech starts or stays safe without TTS engine',
                  bool(find_all(xml, text='⏸ Stop', clazz='Button')) or bool(find_all(xml, text='🔊 Speak question', clazz='Button')))
            qstop = find_all(xml, text='⏸ Stop', clazz='Button')
            if qstop:
                tap_node(qstop[0])
                time.sleep(0.7)
                xml = dump()
                check('quiz speech stop works', bool(find_all(xml, text='🔊 Speak question', clazz='Button')))
        shot('03_topic_quiz')
        run(['adb', 'shell', 'input', 'keyevent', '4'])
        time.sleep(1)
        nodes, xml = wait_node(15, contains='Linux App')
        PAGES.append(xml)
        check('back from quiz returns home', bool(nodes))

    # Interview Questions section: only Q&A, one card per topic area
    nodes, xml = find_scrolled(contains='Interview Questions - Topic-wise')
    check('interview section header present', bool(nodes))
    shot('04_interview_section')

    nodes, xml = find_scrolled(contains='Navigation & the Filesystem Interview Questions')
    check('topic interview card present', bool(nodes))
    if nodes:
        tap_node(nodes[0])
        nodes, xml = wait_node(20, contains='tap Show answer or listen')
        check('interview topic opens', bool(nodes))
        check('interview topic shows its area name', bool(find_all(xml, contains='Navigation & the Filesystem Interview Questions')))
        check('filesystem diagram caption present', bool(find_all(xml, contains='Diagram: Linux filesystem hierarchy')))
        play_all = find_all(xml, text='🔊 Play all topic', clazz='Button')
        check('play-all topic button present', bool(play_all))
        if play_all:
            tap_node(play_all[0])
            time.sleep(2)
            xml = dump()
            check('play-all starts or stays safe without TTS engine',
                  bool(find_all(xml, text='⏸ Stop topic', clazz='Button')) or bool(find_all(xml, text='🔊 Play all topic', clazz='Button')))
            stop_all = find_all(xml, text='⏸ Stop topic', clazz='Button')
            if stop_all:
                tap_node(stop_all[0])
                time.sleep(0.7)
                xml = dump()
                check('play-all stop works', bool(find_all(xml, text='🔊 Play all topic', clazz='Button')))
        shot('05_interview_list')
        nodes, xml = find_scrolled(max_swipes=12, text='Show answer', clazz='Button')
        check('first question controls reachable below visual guide', bool(nodes))
        speak = find_all(xml, text='🔊 Speak', clazz='Button')
        check('per-question speak button present', bool(speak))
        if speak:
            tap_node(speak[0])
            time.sleep(1.2)
            xml = dump()
            check('question speech starts or stays safe without TTS engine',
                  bool(find_all(xml, text='⏸ Stop', clazz='Button')) or bool(find_all(xml, text='🔊 Speak', clazz='Button')))
            stop = find_all(xml, text='⏸ Stop', clazz='Button')
            if stop:
                tap_node(stop[0])
                time.sleep(0.7)
                xml = dump()
                check('question speech stop works', bool(find_all(xml, text='🔊 Speak', clazz='Button')))
        shot('05b_speak_controls')
        btns = find_all(xml, text='Show answer', clazz='Button')
        check('show answer button present', bool(btns))
        if btns:
            tap_node(btns[0])
            nodes, xml = wait_node(8, contains='absolute path')
            check('answer reveals on tap', bool(nodes))
            shot('06_interview_answer')
        nodes, xml = find_scrolled(max_swipes=24, contains='Command Reference')
        check('command reference present', bool(nodes))
        check('command group listed once', bool(find_all(xml, contains='Navigation & Basics')))
        nodes, xml = find_scrolled(max_swipes=12, contains='ls -la')
        check('command row has example', bool(nodes))
        check('command row has use', bool(find_all(xml, contains='Files, hidden files')))
        cmd_speak = find_all(xml, text='🔊', clazz='Button')
        check('command-row speak button present', bool(cmd_speak))
        if cmd_speak:
            tap_node(cmd_speak[0])
            time.sleep(1.2)
            xml = dump()
            check('command speech starts or stays safe without TTS engine',
                  bool(find_all(xml, text='⏸', clazz='Button')) or bool(find_all(xml, text='🔊', clazz='Button')))
            cmd_stop = find_all(xml, text='⏸', clazz='Button')
            if cmd_stop:
                tap_node(cmd_stop[0])
                time.sleep(0.7)
                xml = dump()
                check('command speech stop works', bool(find_all(xml, text='🔊', clazz='Button')))
        shot('06b_command_reference')
        run(['adb', 'shell', 'input', 'keyevent', '4'])
        time.sleep(1)
        nodes, xml = wait_node(15, contains='Linux App')
        PAGES.append(xml)

    # Visual guides: open each mapped topic and capture every bundled diagram
    def check_diagram_topic(needle, captions, shot_prefix):
        nodes, xml = find_scrolled(max_swipes=30, contains=needle)
        check('diagram topic card present: ' + needle, bool(nodes))
        if not nodes:
            return
        tap_node(nodes[0])
        nodes, xml = wait_node(20, contains='Visual guide')
        check('visual guide opens: ' + needle, bool(nodes))
        for idx, caption in enumerate(captions, 1):
            nodes, xml = find_scrolled(max_swipes=10, contains=caption)
            check('diagram caption visible: ' + caption, bool(nodes))
            shot('%s_%d' % (shot_prefix, idx))
        run(['adb', 'shell', 'input', 'keyevent', '4'])
        time.sleep(1)
        nodes, xml = wait_node(15, contains='Linux App')
        PAGES.append(xml)

    check_diagram_topic('Users, Groups & Permissions Interview Questions',
        ['Diagram: rwx permissions and octal values'], '12_diagram_permissions')
    check_diagram_topic('Processes & Job Control Interview Questions',
        ['Diagram: Linux process states'], '13_diagram_process_states')
    check_diagram_topic('Networking Essentials Interview Questions',
        ['Diagram: OSI and TCP/IP models', 'Diagram: TCP three-way handshake'], '14_diagram_network')
    check_diagram_topic('Disks, Filesystems & Storage Interview Questions',
        ['Diagram: LVM layers', 'Diagram: RAID levels'], '15_diagram_storage')
    check_diagram_topic('Boot, systemd & Services Interview Questions',
        ['Diagram: Linux boot process', 'Diagram: systemd units and dependencies'], '16_diagram_systemd')
    check_diagram_topic('Advanced Security Interview Questions',
        ['Diagram: SELinux decision flow'], '17_diagram_selinux')
    check_diagram_topic('Containers & KVM Interview Questions',
        ['Diagram: Docker containers vs virtual machines'], '18_diagram_docker_vm')
    check_diagram_topic('Advanced Storage & Shares Interview Questions',
        ['Diagram: NFS and Samba file sharing'], '19_diagram_nfs_samba')

    # HR & Closing Round is the last interview topic card
    nodes, xml = find_scrolled(max_swipes=30, contains='HR & Closing Round Interview Questions')
    check('HR & closing interview card present', bool(nodes))

    # Learn section: chapter cards with lessons
    nodes, xml = find_scrolled(contains='Learn - Lessons by Chapter')
    check('learn section header present', bool(nodes))
    chap, xml = find_scrolled(text='Linux History & Foundations')
    check('chapter list present', bool(chap))

    # The old mixed "Interview Mode" quiz card must be gone from every home page we saw
    check("no 'Interview Mode' quiz card anywhere on home",
          all('Interview Mode' not in page for page in PAGES))

    if not chap:
        die('chapter card not found')
    tap_node(chap[0])
    nodes, xml = wait_node(20, contains='Start Quiz')
    check('chapter page opens with Start Quiz', bool(nodes))
    check('lessons listed', bool(find_all(xml, contains='short lessons')))
    shot('07_chapter')
    les = find_all(xml, contains='What is Linux, really?')
    if les:
        tap_node(les[0])
        nodes, xml = wait_node(20, contains='Lesson 1 of')
        check('lesson opens with body', bool(nodes) and bool(find_all(xml, contains='KERNEL')))
        check('diagram rendered', bool(find_all(xml, contains='HARDWARE')))
        check('real-life scenario shown', bool(find_all(xml, contains="Where you'd use it")))
        listen = find_all(xml, text='🔊 Listen to lesson', clazz='Button')
        check('lesson listen button present', bool(listen))
        if listen:
            tap_node(listen[0])
            time.sleep(1.2)
            xml = dump()
            check('lesson speech starts or stays safe without TTS engine',
                  bool(find_all(xml, text='⏸ Stop', clazz='Button')) or bool(find_all(xml, text='🔊 Listen to lesson', clazz='Button')))
            stop = find_all(xml, text='⏸ Stop', clazz='Button')
            if stop:
                tap_node(stop[0])
                time.sleep(0.7)
                xml = dump()
                check('lesson speech stop works', bool(find_all(xml, text='🔊 Listen to lesson', clazz='Button')))
        shot('08_lesson')
        nodes = []
        for _ in range(5):
            run(['adb', 'shell', 'input', 'swipe', '480', '1600', '480', '250', '450'])
            nodes, xml = wait_node(2, contains='Quick check')
            if nodes and find_all(xml, text='Linux kernel', clazz='Button'):
                break
        check('lesson quick check visible', bool(nodes))
        shot('08b_quick_check')
        q_answer = find_all(xml, text='Linux kernel', clazz='Button')
        if q_answer:
            tap_node(q_answer[0])
            nodes, xml = wait_node(5, contains='You got it!')
            if not nodes:
                run(['adb', 'shell', 'input', 'swipe', '480', '1450', '480', '450', '450'])
                nodes, xml = wait_node(8, contains='You got it!')
            check('correct quick check gives feedback', bool(nodes))
            shot('08c_check_passed')
        else:
            check('correct quick check gives feedback', False, 'answer option not found')
        run(['adb', 'shell', 'input', 'keyevent', '4'])
        time.sleep(1)
        nodes, xml = wait_node(15, contains='Linux App')
        chap, xml = find_scrolled(max_swipes=30, text='Linux History & Foundations')
        check('chapter card reachable after lesson', bool(chap))
        if chap:
            tap_node(chap[0])
            nodes, xml = wait_node(15, contains='Start Quiz')
    start = find_all(xml, contains='Start Quiz')
    if not start:
        die('Start Quiz button not found')
    tap_node(start[0])
    nodes, xml = wait_node(20, contains='Question 1 of')
    check('quiz opens at question 1', bool(nodes))
    opts = [n for n in find_all(xml, clazz='Button') if (n.get('text') or '').strip() and n.get('text') not in ('Next', 'See results', '🔊 Speak question', '⏸ Stop')]
    check('4 options shown', len(opts) == 4, 'found %d' % len(opts))
    shot('09_question')
    if not opts:
        die('no option buttons')
    tap_node(opts[0])
    nodes, xml = wait_node(15, regex=r'^(Next|See results)$')
    check('answer feedback + explanation shown', bool(nodes) and bool(find_all(xml, contains='Correct') + find_all(xml, contains='Not quite')))
    shot('10_feedback')
    nxt = find_all(xml, regex=r'^(Next|See results)$')
    if nxt:
        tap_node(nxt[0])
        nodes, xml = wait_node(15, contains='Question 2 of')
        check('next advances to question 2', bool(nodes))
        shot('11_question2')
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
