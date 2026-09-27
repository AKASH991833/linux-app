# Linux App

Master Linux, step by step. A fully offline Android app covering the complete
Linux system administrator syllabus - beginner lessons, topic-wise quizzes and
topic-wise interview questions, from history and navigation to systemd, storage
and security.

## Features

- **Learn mode** - every chapter starts with 4 short beginner lessons: simple
  day-one language, ASCII diagrams (filesystem tree, rwx permissions, process
  tree, boot flow, LVM stack, cron fields...) and real command examples with
  sample output. Read ticks track your progress
- **12 chapters, 300 quiz questions** - 25 hand-written MCQs per chapter (4
  options, one correct, short explanation for every answer)
- **237 interview Q&A + 204 command examples** - real interview questions per
  topic area with exact, verified answers, plus practical command/example/use
  reference tables and an HR & closing-round set
  (`app/src/main/assets/interview.json`)
- **Chapter-wise practice** - Basic to advanced, one concept area at a time
- **Quiz section - topic-wise** - every topic area has its own clearly labelled
  quiz (Beginner untimed, Intermediate 30s per question). Only quizzes here
- **Interview Questions section - topic-wise** - every topic area has its own
  set of real interview questions with verified answers; tap to reveal. Each
  matching topic also groups practical commands once, in clean
  command/example/use rows. No quiz here
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
drives the UI (open a chapter, answer a question, check feedback, navigate
back) and publishes screenshots to the `emulator-shots-api29` branch.

## Tech

Native Android, single Activity, programmatic UI (no XML layouts), Java 17,
minSdk 26, targetSdk 35. Question bank lives in `app/src/main/assets/questions.json`.
Progress, streaks and the mistake list persist in SharedPreferences.
