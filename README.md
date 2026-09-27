# Linux App

Master Linux, one quiz at a time. A fully offline Android quiz app covering the
complete Linux system administrator syllabus - from history and navigation to
systemd, storage and security.

## Features

- **12 chapters, 300 questions** - 25 hand-written MCQs per chapter (4 options,
  one correct, short explanation for every answer)
- **Chapter-wise practice** - Basic to advanced, one concept area at a time
- **Levels** - Beginner (untimed), Intermediate (30s per question), Interview
  mode (15 mixed questions, 20s each)
- **Instant feedback** - right/wrong highlighting plus an explanation card
- **Score & progress** - best score per chapter, animated progress bars
- **Revise mistakes** - wrong answers are collected automatically; answer them
  right to clear the list
- **Daily challenge + streak** - 10 fresh questions every day, keep the streak alive
- **Fully offline** - no internet, no account, no ads
- **Dark UI** with smooth transitions and micro-animations

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
