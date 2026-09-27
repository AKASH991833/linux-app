# Linux App

Master Linux, step by step. A fully offline Android app with a simple home
dashboard: Beginner quizzes, Intermediate quizzes, Interview Questions and
Learn Chapters. Each tile opens a clean topic-wise or chapter-wise list, and
every interview answer opens as a readable book-style page.

## Features

- **Simple home dashboard** - four big entry tiles only: Beginner quizzes,
  Intermediate quizzes, Interview Questions and Learn Chapters. Topic cards stay
  inside their section, so the home screen remains clean
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
- **Offline Speak mode** - listen to book-style interview answers, full lessons,
  individual command rows, quiz questions/options/explanations, or play a whole
  interview topic for revision; play/stop uses
  Android TextToSpeech and needs no internet or new permission
- **Chapter-wise practice** - Basic to advanced, one concept area at a time
- **Quiz section - topic-wise** - every topic area has its own clearly labelled
  quiz (Beginner untimed, Intermediate 30s per question). Only quizzes here
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
drives the dashboard UI (open Beginner/Intermediate topic lists, interview
chapters and Learn chapters, answer a question, check feedback, navigate back),
checks the Speak controls, opens every named source section and the book-style
reader, confirms generated diagrams and collapsed answer cards are absent, and
publishes screenshots to the `emulator-shots-api29` branch.

## Tech

Native Android, single Activity, programmatic UI (no XML layouts), Java 17,
minSdk 26, targetSdk 35. Question bank lives in `app/src/main/assets/questions.json`.
Progress, streaks and the mistake list persist in SharedPreferences.
