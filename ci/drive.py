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
    check('home shows v1.3 content marker', bool(find_all(xml, contains='v1.3.0')) and bool(find_all(xml, contains='328 interview Q&A')))
    check('dashboard tiles present',
          bool(find_all(xml, text='Beginner')) and bool(find_all(xml, text='Intermediate')) and
          bool(find_all(xml, text='Interview Questions')) and bool(find_all(xml, text='Learn Chapters')))
    check('home has no topic cards', not find_all(xml, contains='Linux History & Foundations Quiz'))
    check('home has no generated diagrams', not find_all(xml, contains='Diagram:') and not find_all(xml, contains='Visual guide'))
    shot('01_home')

    # Beginner tile opens topic-wise quizzes.
    beginner = find_all(xml, text='Beginner')
    check('beginner tile present', bool(beginner))
    if beginner:
        tap_node(beginner[0])
        nodes, xml = wait_node(20, contains='Beginner Quiz Topics')
        PAGES.append(xml)
        check('beginner topic list opens', bool(nodes))
        shot('02_beginner_topics')
        nodes, xml = find_scrolled(contains='Linux History & Foundations Quiz')
        check('topic quiz card present', bool(nodes))
        if nodes:
            tap_node(nodes[0])
            nodes, xml = wait_node(20, contains='Question 1 of')
            check('topic quiz opens at question 1', bool(nodes))
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
            shot('03_topic_quiz')
            run(['adb', 'shell', 'input', 'keyevent', '4'])
            time.sleep(1)
            nodes, xml = wait_node(15, contains='Home Dashboard')
            PAGES.append(xml)

    # Interview tile opens named source sections plus topic-wise chapters.
    interview = find_all(xml, text='Interview Questions')
    check('interview tile present', bool(interview))
    if interview:
        tap_node(interview[0])
        nodes, xml = wait_node(20, contains='Your Questions & PDFs')
        PAGES.append(xml)
        check('interview source sections open', bool(nodes))
        check('all four named sections visible',
              bool(find_all(xml, contains='My Interview Questions')) and
              bool(find_all(xml, contains='200 Important Commands')) and
              bool(find_all(xml, contains='Networking Interview Q&A')) and
              bool(find_all(xml, contains='15 Advanced Topics Handbook')))
        shot('04_interview_sections')

        # His original 52 Q&A, in order, book-style.
        nodes, xml = find_scrolled(contains='My Interview Questions')
        if nodes:
            tap_node(nodes[0])
            nodes, xml = wait_node(20, contains='52 Q&A')
            check('My Interview Questions opens with 52 items', bool(nodes))
            shot('05_my_questions')
            first = find_all(xml, contains='How is Windows different from Linux?')
            check('first original question visible', bool(first))
            if first:
                tap_node(first[0])
                nodes, xml = wait_node(20, contains='My Interview Questions  •  1 of 52')
                check('book reader opens first answer', bool(nodes))
                check('his original answer is fully visible', bool(find_all(xml, contains='Windows is a commercial operating system')))
                check('reader speak button present', bool(find_all(xml, text='🔊 Speak answer', clazz='Button')))
                shot('06_my_book_answer')
                nxt = find_all(xml, text='Next →', clazz='Button')
                check('reader next button present', bool(nxt))
                if nxt:
                    tap_node(nxt[0])
                    nodes, xml = wait_node(10, contains='2 of 52')
                    check('reader next page works', bool(nodes) and bool(find_all(xml, contains='Difference between Unix and Linux?')))
                    shot('07_my_next_page')
            run(['adb', 'shell', 'input', 'keyevent', '4'])
            time.sleep(1)
            nodes, xml = wait_node(15, contains='Home Dashboard')
            PAGES.append(xml)

        # 200 command PDF as its own table.
        interview = find_all(xml, text='Interview Questions')
        if interview:
            tap_node(interview[0])
            nodes, xml = wait_node(20, contains='Your Questions & PDFs')
            nodes, xml = find_scrolled(contains='200 Important Commands')
            check('200 commands section present', bool(nodes))
            if nodes:
                tap_node(nodes[0])
                nodes, xml = wait_node(20, contains='all 200 rows in original order')
                check('200 commands screen opens', bool(nodes))
                check('first command row visible', bool(find_all(xml, contains='#1  pwd')))
                check('topic header visible once', bool(find_all(xml, text='Navigation & Basics')))
                check('command example visible', bool(find_all(xml, contains='Example: pwd')))
                check('command speak present', bool(find_all(xml, text='🔊', clazz='Button')))
                shot('08_commands200')
                run(['adb', 'shell', 'input', 'keyevent', '4'])
                time.sleep(1)
                nodes, xml = wait_node(15, contains='Home Dashboard')
                PAGES.append(xml)

        # Networking PDF, all 40 Q&A in original order.
        interview = find_all(xml, text='Interview Questions')
        if interview:
            tap_node(interview[0])
            nodes, xml = wait_node(20, contains='Your Questions & PDFs')
            nodes, xml = find_scrolled(contains='Networking Interview Q&A')
            check('networking source section present', bool(nodes))
            if nodes:
                tap_node(nodes[0])
                nodes, xml = wait_node(20, contains='all 40 Q&A in original order')
                check('networking source opens', bool(nodes))
                shot('09_networking_source')
                first = find_all(xml, contains='What is a network?')
                check('first networking PDF question visible', bool(first))
                if first:
                    tap_node(first[0])
                    nodes, xml = wait_node(20, contains='Networking Interview Q&A  •  1 of 40')
                    check('networking book reader opens', bool(nodes))
                    check('English answer visible without Hinglish', bool(find_all(xml, contains='Answer: A network is a group')) and not find_all(xml, contains='Hinglish:'))
                    shot('10_networking_book_answer')
                run(['adb', 'shell', 'input', 'keyevent', '4'])
                time.sleep(1)
                nodes, xml = wait_node(15, contains='Home Dashboard')
                PAGES.append(xml)

        # 15 Advanced Topics Handbook and one topic reader.
        interview = find_all(xml, text='Interview Questions')
        if interview:
            tap_node(interview[0])
            nodes, xml = wait_node(20, contains='Your Questions & PDFs')
            nodes, xml = find_scrolled(contains='15 Advanced Topics Handbook')
            check('advanced handbook section present', bool(nodes))
            if nodes:
                tap_node(nodes[0])
                nodes, xml = wait_node(20, contains='15 practical topics in original order')
                check('advanced handbook opens', bool(nodes))
                check('first handbook topic visible', bool(find_all(xml, contains='1. Backup & Restore')))
                shot('11_advanced_handbook')
                first = find_all(xml, contains='1. Backup & Restore')
                if first:
                    tap_node(first[0])
                    nodes, xml = wait_node(20, contains='Backup & Restore Interview Questions')
                    check('handbook topic opens as question list', bool(nodes))
                    check('question rows use book reader links', bool(find_all(xml, contains='Read answer')))
                    shot('12_advanced_topic')
                    q = find_all(xml, contains='What is the difference between backup and synchronization?')
                    if q:
                        tap_node(q[0])
                        nodes, xml = wait_node(20, contains='Backup & Restore Interview Questions  •  1 of')
                        check('topic book reader opens', bool(nodes) and bool(find_all(xml, contains='A backup is a separate copy')))
                        shot('13_advanced_book_answer')
                    run(['adb', 'shell', 'input', 'keyevent', '4'])
                    time.sleep(1)
                    nodes, xml = wait_node(15, contains='Home Dashboard')
                    PAGES.append(xml)

        # Topic-wise sets are still present and use the same book reader.
        interview = find_all(xml, text='Interview Questions')
        if interview:
            tap_node(interview[0])
            nodes, xml = wait_node(20, contains='Your Questions & PDFs')
            nodes, xml = find_scrolled(max_swipes=20, contains='Navigation & the Filesystem Interview Questions')
            check('topic-wise interview chapter present', bool(nodes))
            if nodes:
                tap_node(nodes[0])
                nodes, xml = wait_node(20, contains='Navigation & the Filesystem Interview Questions')
                check('topic-wise chapter opens', bool(nodes))
                check('no collapsed Show answer cards', not find_all(xml, text='Show answer', clazz='Button'))
                shot('14_topic_interview')
                q = find_all(xml, contains='What is the difference between an absolute and relative path?')
                if q:
                    tap_node(q[0])
                    nodes, xml = wait_node(20, contains='Navigation & the Filesystem Interview Questions  •')
                    check('topic answer opens as full page', bool(nodes) and bool(find_all(xml, contains='absolute path')))
                    shot('15_topic_book_answer')
                run(['adb', 'shell', 'input', 'keyevent', '4'])
                time.sleep(1)
                nodes, xml = wait_node(15, contains='Home Dashboard')
                PAGES.append(xml)

    # Learn tile still opens chapter-wise lessons.
    learn = find_all(xml, text='Learn Chapters')
    check('learn tile present', bool(learn))
    if not learn:
        die('learn tile not found')
    tap_node(learn[0])
    nodes, xml = wait_node(20, contains='Learn Chapters')
    PAGES.append(xml)
    check('learn chapter list opens', bool(nodes))
    chap, xml = find_scrolled(text='Linux History & Foundations')
    check('chapter list present', bool(chap))
    shot('16_learn_topics')
    if not chap:
        die('chapter card not found')
    tap_node(chap[0])
    nodes, xml = wait_node(20, contains='Start Quiz')
    check('chapter page opens with Start Quiz', bool(nodes))
    shot('17_chapter')
    les = find_all(xml, contains='What is Linux, really?')
    if les:
        tap_node(les[0])
        nodes, xml = wait_node(20, contains='Lesson 1 of')
        check('lesson opens with body', bool(nodes) and bool(find_all(xml, contains='KERNEL')))
        check('lesson has no diagram block', not find_all(xml, contains='HARDWARE') and not find_all(xml, contains='Diagram:'))
        listen = find_all(xml, text='🔊 Listen to lesson', clazz='Button')
        check('lesson listen button present', bool(listen))
        shot('18_lesson')
        run(['adb', 'shell', 'input', 'keyevent', '4'])
        time.sleep(1)
        nodes, xml = wait_node(15, contains='Home Dashboard')
        learn = find_all(xml, text='Learn Chapters')
        if learn:
            tap_node(learn[0])
            nodes, xml = wait_node(15, contains='Learn Chapters')
            chap, xml = find_scrolled(max_swipes=10, text='Linux History & Foundations')
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
    shot('19_question')
    if not opts:
        die('no option buttons')
    tap_node(opts[0])
    nodes, xml = wait_node(15, regex=r'^(Next|See results)$')
    check('answer feedback + explanation shown', bool(nodes) and bool(find_all(xml, contains='Correct') + find_all(xml, contains='Not quite')))
    shot('20_feedback')
    run(['adb', 'shell', 'input', 'keyevent', '4'])
    time.sleep(1)
    xml = dump()
    check('back returns home', bool(find_all(xml, contains='Home Dashboard')))
    check("no generated diagram labels anywhere in tested screens",
          all('Diagram:' not in page and 'Visual guide' not in page for page in PAGES))
    check("no old collapsed answer controls anywhere in tested screens",
          all('Show answer' not in page and 'Hide answer' not in page for page in PAGES))
    r = run(['adb', 'logcat', '-d'])
    logs = r.stdout.decode('utf-8', 'ignore')
    fatal = [l for l in logs.splitlines() if 'FATAL EXCEPTION' in l and PKG in l]
    check('no fatal crash in logcat', not fatal, fatal[0] if fatal else '')
    failed = [n for n, ok in results if not ok]
    finish(1 if failed else 0)

main()
