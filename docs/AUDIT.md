# App audit – 23 Nov 2026 (v2.1.0)

A review of the whole app after the v2.0 changes (Amazfit watch, calorie goal,
activity score, history, redesign). "Fixed" items are in v2.1.0.

## Fixed in v2.1.0

| # | Problem | Impact | Fix |
|---|---------|--------|-----|
| 1 | Every GitHub build was signed with a new, random debug key | New APKs would not install over the old one ("App not installed"); you had to uninstall and lose your data | Builds are now signed with one fixed test key (`app/ci-debug.keystore`). **Uninstall once** to switch to it; after that, updates install normally |
| 2 | "Quit" reset today's steps, distance and calories to zero | Quitting wiped the day's count, which then also went missing from History | Quit now only stops counting; totals are kept |
| 3 | "Pause" was undone automatically after 10 minutes away | Counting restarted without being asked | Counting stays paused until you choose Resume |
| 4 | Counting restarted after a reboot, but the screen could not see it | The main screen could show stale numbers after a reboot | The screen now starts and connects to the counter whenever counting is on |
| 5 | Re-opening the app called `startForegroundService` on an already running counter without re-confirming the foreground notification | Possible "did not call startForeground" crash on some phones | The service re-confirms foreground mode on every start |
| 6 | The counter kept calling back into the closed screen | Memory leak while the screen was closed | The callback is cleared when the screen is hidden; unbinding is now safe to call twice |
| 7 | The voice helper kept a reference to the stopped service | Memory leak after Quit | Released on shutdown |
| 8 | Amazfit watch data needed all five Health Connect permissions | If you allowed only steps, the watch card kept asking forever | Steps alone is enough; distance, calories and heart rate are used when allowed. If heart rate is missing, the score card asks for it |
| 9 | The activity score re-read 7 days of heart rate every minute | Slow, battery-hungry, could hit Health Connect read limits | Re-read every 15 minutes, and each time the screen opens |
| 10 | The notification always said "Counting your steps" | No way to see progress without opening the app | Shows today's steps, updated every 10 steps |
| 11 | No feedback when the step goal was reached | — | One quiet notification per day when the goal is reached |
| 12 | The step ring had no spoken description | Screen-reader users heard only the number | Reads out e.g. "6,240 of 8,000 steps" |
| 13 | No automated tests | Regressions go unnoticed | Unit tests for the activity-score maths and the step listener order; they run on every GitHub build |

## Fixed in v2.2.0

| # | Problem | Fix |
|---|---------|-----|
| A | The counter held a CPU wake lock all day | Counts with the phone's step-counter chip, which keeps counting in hardware while the phone sleeps; no wake lock. Pace still comes live from the step-detector chip while you walk. Steps the chip counted while the app was killed or the phone rebooted are caught up. The wake lock is only used for the old accelerometer method, or if you choose "Screen on" / "Aggressive" |
| J | Settings and History used the old Android screens | Settings rebuilt with current Android components (switches, current values shown, Material 3 dark theme). History shows a 7-day summary and a card per day with progress toward the step goal |
| — | No way to see the phone's steps in Google Health | New "Share steps with Google Health" setting writes the phone's steps and distance to Health Connect every 15 minutes |

## Fixed in v2.3.0 (Google Play readiness)

| # | Problem | Fix |
|---|---------|-----|
| B | Targeted Android 14; Google Play requires Android 16 (API 36) | Targets API 36. Every screen now has its own toolbar and keeps clear of the status bar, navigation bar and cutout (Android 16 removed the edge-to-edge opt-out) |
| C | Only a debug-signed APK | GitHub Actions builds a signed release bundle (`pedometer-release.aab`) once the upload key is added as secrets |
| — | Used the original author's package name | Own package name `io.github.markybadz.pedometer` |
| E | Voice prompts English-only, voice fixed to US English | Prompts in `strings.xml` (translatable); voice follows the phone's language, falling back to English |
| — | No privacy policy, store assets or checklist | `docs/PRIVACY.md`, `docs/PLAY_STORE.md`, new adaptive app icon, 512 px icon and feature graphic |
| — | Libraries could quietly add internet access | Internet permission explicitly removed |

## Still open

| # | Gap | Impact | Suggested fix |
|---|-----|--------|---------------|
| D | Distance and speed are stored in whatever unit is set at the time | Switching between km and miles mid-day mixes the two in today's total | Store metres internally and convert only for display |
| F | The calorie goal uses the phone's estimate only | Calories the watch records while the phone is left behind don't count toward the goal | Optionally use the higher of phone and watch calories |
| G | Calorie estimate is a fixed factor × weight × distance | Rough; ignores pace, incline and heart rate | When watch calories are available, prefer them |
| H | Watch data is only as fresh as the Zepp app's last sync | The watch card can lag by 15–30 minutes | Nothing to fix in this app; the card shows the last sync time |
| I | The activity score is an estimate | Will not match Zepp's own PAI number; Zepp doesn't share PAI with other apps | Labelled as an estimate in the app; keep it that way |
| L | With "Watch app: Any app in Health Connect" and Google Health sharing both on, the watch card also counts this phone's own shared steps | Watch total can include phone steps | Choose Zepp or Zepp Life as the watch app (the default) |
