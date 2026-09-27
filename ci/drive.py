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

def y1(n):
    m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.get('bounds'))
    return int(m.group(2))

def tap_option(xml, index=0):
    rows = [n for n in find_all(xml, clazz='LinearLayout', clickable=True) if y1(n) > 520]
    rows.sort(key=y1)
    if len(rows) > index:
        tap_node(rows[index])
        return True
    return False

def tap_exact(xml, label, contains=False):
    nodes = find_all(xml, contains=label) if contains else find_all(xml, text=label)
    if nodes:
        tap_node(nodes[0])
        return True
    return False

def main():
    r = run(['adb', 'install', '-r', 'app/build/outputs/apk/debug/app-debug.apk'])
    out = r.stdout.decode('utf-8', 'ignore') + r.stderr.decode('utf-8', 'ignore')
    check('apk installs', 'Success' in out, out.strip().splitlines()[-1] if out.strip() else '')
    run(['adb', 'shell', 'monkey', '-p', PKG, '-c', 'android.intent.category.LAUNCHER', '1'])

    nodes, xml = wait_node(20, contains='Loading your learning journey')
    PAGES.append(xml)
    check('splash screen opens', bool(nodes))
    shot('01_splash')

    nodes, xml = wait_node(20, contains='Welcome to')
    PAGES.append(xml)
    check('welcome screen opens', bool(nodes))
    check('welcome feature list present', bool(find_all(xml, contains='MCQ Quizzes with Explanations')) and bool(find_all(xml, contains='Real Interview Questions')))
    shot('02_welcome')
    if not tap_exact(xml, 'Get Started'):
        die('welcome get started button not found')

    nodes, xml = wait_node(20, contains='Continue Learning')
    PAGES.append(xml)
    check('home dashboard opens', bool(nodes))
    check('home greeting present', bool(find_all(xml, regex=r'Good (Morning|Afternoon|Evening), Akash!')))
    check('home action cards present',
          bool(find_all(xml, text='Linux Quiz')) and bool(find_all(xml, text='Computer Quiz')) and
          bool(find_all(xml, text='Full Forms')) and bool(find_all(xml, text='Interview Questions')))
    progress_nodes, progress_xml = find_scrolled(8, text='Your Progress')
    check('progress card present', bool(progress_nodes) and bool(find_all(progress_xml, contains='Interview Q&A')) and bool(find_all(progress_xml, contains='Acronyms')))
    shot('03b_home_progress')
    scroll_up(8)
    nodes, xml = wait_node(10, contains='Continue Learning')
    check('bottom navigation present',
          bool(find_all(xml, text='Home')) and bool(find_all(xml, text='Topics')) and bool(find_all(xml, text='Quiz')) and
          bool(find_all(xml, text='Interview')) and bool(find_all(xml, text='More')))
    check('no generated diagrams', not find_all(xml, contains='Diagram:') and not find_all(xml, contains='Visual guide'))
    shot('03_home')

    # Quiz setup and centered question/review flow.
    check('linux quiz card present', tap_exact(xml, 'Linux Quiz'))
    nodes, xml = wait_node(20, contains='Create Quiz')
    PAGES.append(xml)
    check('quiz setup opens', bool(nodes))
    check('quiz setup topic controls present',
          bool(find_all(xml, text='Select Topic')) and bool(find_all(xml, text='Linux - Beginner')))
    shot('04_quiz_setup')
    control_nodes, control_xml = find_scrolled(10, text='Difficulty Level')
    check('quiz setup controls present',
          bool(control_nodes) and bool(find_all(control_xml, text='Number of Questions')))
    check('quiz setup difficulty and count present',
          bool(find_all(control_xml, text='Easy')) and bool(find_all(control_xml, text='Normal')) and bool(find_all(control_xml, text='Hard')) and
          bool(find_all(control_xml, text='10')) and bool(find_all(control_xml, text='20')) and bool(find_all(control_xml, text='30')))
    start_nodes, xml = find_scrolled(10, text='Start Quiz')
    shot('04b_quiz_setup_controls')
    if not start_nodes:
        die('start quiz button not found')
    tap_node(start_nodes[0])
    nodes, xml = wait_node(20, contains='/10')
    PAGES.append(xml)
    check('quiz question opens centered flow', bool(nodes) and bool(find_all(xml, text='Speak question', clazz='Button')))
    shot('05_quiz_question')
    check('answer option tappable', tap_option(xml, 0))
    time.sleep(1)
    nodes, xml = wait_node(10, contains='Explanation of all options')
    PAGES.append(xml)
    check('answer review explains every option', bool(nodes))
    check('answer review marks correct option', bool(find_all(xml, contains='Correct answer:')) or bool(find_all(xml, text='Correct')))
    shot('06_answer_review')

    # Computer chapter quiz, through a full 5-question round to the result screen.
    run(['adb', 'shell', 'input', 'keyevent', '4'])
    time.sleep(1)
    nodes, xml = wait_node(15, text='Computer Quiz')
    PAGES.append(xml)
    check('returned home after quiz', bool(find_all(xml, text='Continue Learning')))
    check('computer quiz card present', tap_exact(xml, 'Computer Quiz'))
    nodes, xml = wait_node(20, contains='Computer Quiz Chapters')
    PAGES.append(xml)
    check('computer quiz chapters open', bool(nodes) and bool(find_all(xml, contains='Computer Fundamentals')))
    shot('07_computer_chapters')
    if not tap_exact(xml, 'Computer Fundamentals', contains=True):
        die('computer fundamentals chapter not found')
    nodes, xml = wait_node(20, contains='Choose a difficulty')
    PAGES.append(xml)
    check('computer difficulty screen opens', bool(nodes))
    shot('08_computer_difficulty')
    if not tap_exact(xml, 'Easy'):
        die('computer easy button not found')
    for i in range(5):
        nodes, xml = wait_node(20, regex=r'Computer - Computer Fundamentals|Computer • Computer Fundamentals')
        if not nodes:
            die('computer quiz question ' + str(i+1) + ' did not open')
        if i == 0:
            shot('09_computer_question')
        tap_option(xml, 1)  # deliberately wrong: source data has correct answer first
        time.sleep(0.8)
        label = 'See Results' if i == 4 else 'Next Question'
        nodes, xml = wait_node(10, text=label)
        if not nodes:
            die(label + ' button not found')
        tap_node(nodes[0])
        time.sleep(0.8)
    nodes, xml = wait_node(20, text='Quiz Result')
    PAGES.append(xml)
    check('quiz result screen opens', bool(nodes))
    check('result stats present', bool(find_all(xml, text='Correct')) and bool(find_all(xml, text='Incorrect')))
    check('review mistakes offered', bool(find_all(xml, text='Review Mistakes')))
    shot('10_quiz_result')
    if not tap_exact(xml, 'Back to Home'):
        die('result back home button not found')

    # Full Forms search.
    nodes, xml = wait_node(15, text='Full Forms')
    PAGES.append(xml)
    check('full forms card present', tap_exact(xml, 'Full Forms'))
    nodes, xml = wait_node(20, contains='477 commonly used technical abbreviations')
    PAGES.append(xml)
    check('full forms screen opens', bool(nodes))
    check('full form search box present', bool(find_all(xml, clazz='EditText')))
    shot('11_full_forms')
    search = find_all(xml, clazz='EditText')
    if search:
        tap_node(search[0])
        run(['adb', 'shell', 'input', 'text', 'DNS'])
        nodes, xml = wait_node(10, contains='Domain Name System')
        PAGES.append(xml)
        check('full form search finds DNS', bool(nodes))
        shot('12_full_forms_dns')
    run(['adb', 'shell', 'input', 'keyevent', '4'])
    time.sleep(1)

    # Sourced Linux and Computer definitions.
    nodes, xml = wait_node(15, text='Definitions')
    PAGES.append(xml)
    check('definitions card present', tap_exact(xml, 'Definitions'))
    nodes, xml = wait_node(20, contains='Online-sourced definitions')
    PAGES.append(xml)
    check('definitions screen opens', bool(nodes) and bool(find_all(xml, text='Linux')) and bool(find_all(xml, text='Computer')))
    check('definitions source labels present', bool(find_all(xml, contains='Source:')))
    search = find_all(xml, clazz='EditText')
    check('definitions search box present', bool(search))
    if search:
        tap_node(search[0])
        run(['adb', 'shell', 'input', 'text', 'Kernel'])
        nodes, xml = wait_node(10, text='Kernel')
        PAGES.append(xml)
        check('definitions search finds Kernel', bool(nodes) and bool(find_all(xml, contains='Linux Glossary')))
        shot('12b_definitions')
    run(['adb', 'shell', 'input', 'keyevent', '4'])
    time.sleep(1)

    # Interview source sections and book-style question screen.
    nodes, xml = wait_node(15, text='Interview Questions')
    PAGES.append(xml)
    check('interview card present', tap_exact(xml, 'Interview Questions'))
    nodes, xml = wait_node(20, contains='Your Questions & PDFs')
    PAGES.append(xml)
    check('interview sections open', bool(nodes))
    check('all four named interview sections visible',
          bool(find_all(xml, contains='My Interview Questions')) and bool(find_all(xml, contains='200 Important Commands')) and
          bool(find_all(xml, contains='Networking Interview Q&A')) and bool(find_all(xml, contains='15 Advanced Topics Handbook')))
    shot('13_interview_sections')
    nodes, xml = find_scrolled(contains='My Interview Questions')
    if nodes:
        tap_node(nodes[0])
        nodes, xml = wait_node(20, contains='52 Q&A')
        check('My Interview Questions opens', bool(nodes))
        shot('14_my_questions')
        first = find_all(xml, contains='How is Windows different from Linux?')
        if first:
            tap_node(first[0])
            nodes, xml = wait_node(20, contains='Interview Question')
            PAGES.append(xml)
            check('interview question screen opens', bool(nodes) and bool(find_all(xml, text='Speak answer', clazz='Button')))
            shot('15_interview_question')
    run(['adb', 'shell', 'input', 'keyevent', '4'])
    time.sleep(1)
    run(['adb', 'shell', 'input', 'keyevent', '4'])
    time.sleep(1)

    # Topics -> chapter home -> lesson list -> lesson.
    nodes, xml = wait_node(15, text='Topics')
    PAGES.append(xml)
    check('topics tab present', tap_exact(xml, 'Topics'))
    nodes, xml = wait_node(20, contains='All Topics')
    PAGES.append(xml)
    check('topics list opens', bool(nodes) and bool(find_all(xml, contains='Linux History & Foundations')))
    shot('16_topics')
    if not tap_exact(xml, 'Linux History & Foundations', contains=True):
        die('first topic not found')
    nodes, xml = wait_node(20, contains='Concepts & Notes')
    PAGES.append(xml)
    check('chapter home opens', bool(nodes) and bool(find_all(xml, contains='lessons completed')))
    shot('17_chapter_home')
    if not tap_exact(xml, 'Concepts & Notes'):
        die('concepts notes row not found')
    nodes, xml = wait_node(20, contains='What is Linux, really?')
    PAGES.append(xml)
    check('chapter lessons list opens', bool(nodes))
    shot('18_lessons_list')
    if not tap_exact(xml, 'What is Linux, really?', contains=True):
        die('first lesson not found')
    nodes, xml = wait_node(20, contains='Quick check')
    PAGES.append(xml)
    check('lesson reader opens with quick check', bool(nodes))
    check('lesson speak remains available', bool(find_all(xml, contains='Listen to lesson')))
    shot('19_lesson')

    failed = [n for n, ok in results if not ok]
    finish(1 if failed else 0)

if __name__ == '__main__':
    main()
