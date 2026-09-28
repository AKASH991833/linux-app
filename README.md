# Linux App

Master Linux, step by step. A fully offline Android app with a simple home
dashboard: Linux Quiz, Computer Quiz, Full Forms, Interview Questions and Learn
Chapters. Each tile opens a clean level, concept, or chapter list, and every
interview answer opens as a readable book-style page.

## Features

- **Simple home dashboard** - big entry tiles only: Linux Quiz, Computer Quiz,
  Full Forms, Interview Questions and Learn Chapters. Levels, topics and
  chapters stay inside their section, so the home screen remains clean
- **Quiz levels and difficulty** - one Quiz tile opens Beginner, Intermediate,
  or Advanced, then Easy, Normal, or Hard. All 300 questions are tagged across
  the nine level/difficulty sets, with 10 mixed questions per round and harder
  sets using shorter timers
- **Computer Quiz** - 10 computer fundamentals chapters (basics, hardware,
  software, memory/storage, number system, networking, internet, MS Office,
  security and troubleshooting), each with Easy, Normal and Hard sets - 150
  verified questions total
- **477 technical full forms** - searchable concept-wise list covering Linux,
  networking, security, cloud/DevOps, web, databases, hardware, programming and
  IT standards. Speak reads only the abbreviation and its full form
- **Your Questions & PDFs** - four named source sections: My Interview Questions
  (52 Q&A in Akash's original order and wording), 200 Important Commands (all
  200 rows), Networking Interview Q&A (all 40 PDF topics, converted to simple
  English), and 15 Advanced Topics Handbook (Part 2 in original order)
- **Book-style answers** - each interview question opens as its own clean page:
  big question heading, full answer paragraphs, monospace command blocks,
  Previous/Next navigation and Speak
- **12 chapters, 300 quiz questions** - 25 hand-written MCQs per chapter (4
  options, one correct, short explanation for every answer)
- **328 interview Q&A + 346 command examples** - real interview questions per
  topic area with exact, verified answers, plus practical command/example/use
  reference tables and an HR & closing-round set
  (`app/src/main/assets/interview.json`)
- **Focused offline Speak mode** - book pages and topic play-all read only the
  question and answer, command rows read only command plus short use, quiz
  speech reads only the question before feedback and the correct answer after
  feedback, and Full Forms reads only abbreviation plus expansion. Play/stop
  uses Android TextToSpeech and needs no internet or new permission
- **Chapter-wise practice** - Basic to advanced, one concept area at a time
- **Interview Questions section - topic-wise** - every topic area has its own
  set of real interview questions with verified answers; tap a question to open
  its book-style answer page. Each matching topic also groups practical commands once, in clean
  command/example/use rows. No quiz here. Advanced interview-only topics cover
  backup, Ubuntu packages, RAID/NFS/Samba, boot recovery, performance,
  logging, web servers, MySQL, SELinux/sudoers/ACL/fail2ban, LDAP/AD,
  monitoring, Ansible, Docker/KVM, chrony and AWS basics
- **Instant feedback** - right/wrong highlighting plus an explanation card
- **Score & progress** - best score per chapter, animated progress bars
- **Revise mistakes** - wrong answers are collected automatically; answer them
  right to clear the list
- **Daily challenge + streak** - 10 fresh questions every day, keep the streak alive
- **Fully offline** - lessons + quiz, no internet, no account, no ads
- **Polished dark UI** - colour-coded sections (quiz, interview, learn),
  gradient cards, icon chips, smooth transitions and micro-animations

## Chapters

1. Linux History & Foundations
2. Navigation & the Filesystem
3. File & Directory Management
4. Viewing & Working with Text
5. Users, Groups & Permissions
6. Processes & Job Control
7. Package Management
8. Networking Essentials
9. Disks, Filesystems & Storage
10. Shell Scripting Basics
11. Boot, systemd & Services
12. Security, Logs & Scheduled Tasks

## Build

CI builds the debug APK on every push (`.github/workflows/android.yml`) -
download the `linux-app-debug-apk` artifact from the Actions tab.

Locally:

    gradle :app:assembleDebug

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Testing

`emulator-test.yml` boots an Android 10 (API 29) emulator, installs the APK,
drives the dashboard UI (Linux Quiz level/difficulty flow, Computer Quiz
chapter/difficulty flow, searchable Full Forms, interview chapters and Learn
chapters, answer a question, check feedback, navigate back), checks the Speak
controls, opens every named source section and
the book-style reader, confirms generated diagrams and collapsed answer cards
are absent, and publishes screenshots to the `emulator-shots-api29` branch.

## Tech

Native Android, single Activity, programmatic UI (no XML layouts), Java 17,
minSdk 26, targetSdk 35. Question bank lives in `app/src/main/assets/questions.json`.
Progress, streaks and the mistake list persist in SharedPreferences.

## Command guide (v2.2 source)

The separate Commands button opens 20 topics and 200 rows from the user's original
command reference. Each row has a detail page with command-specific options,
examples, when to use them, safety notes, and a manual/reference link. Linux
definitions have short practical examples; the Computer definitions retain their
existing source links. This is a learning aid, not an instruction to run dangerous
commands unchanged on a real system. Check your distribution's manual before a
command changes disks, users, permissions, network access or services.

To regenerate the content: `python3 ci/make_command_guide.py && python3
ci/enrich_content.py`. Review generated examples and links before release.

The Android signed APK workflow uses repository secrets
`LINUX_APP_KEYSTORE_B64` and `LINUX_APP_KEYSTORE_PASSWORD`. Never commit the
keystore or password. Every installable update after v2.1.0 must use the same
signer and a higher versionCode; debug CI APKs from older builds cannot update
an installed version.
