# Pedometer – Privacy Policy

_Last updated: 23 November 2026_

Pedometer ("the app", package `io.github.markybadz.pedometer`) is an open-source step counter published by markybadz. This policy explains what data the app uses and where it goes.

## Summary

- Pedometer has **no internet access**. It does not send any of your data to the developer or to anyone else.
- Your data stays on your phone, in the app's own storage and, **only if you allow it**, in Android's Health Connect.
- There are no ads, no analytics and no accounts.

## Data the app uses

| Data | Where it comes from | Why | Where it's stored |
|------|--------------------|-----|-------------------|
| Steps, distance, pace, speed, calories (estimated) | The phone's step sensors | To count and show your activity | On your phone (app storage) |
| Daily totals for the last 90 days | Calculated by the app | History screen | On your phone (app storage) |
| Settings you enter: step length, body weight, age, resting heart rate, goals | You | Distance, calorie and activity-score calculations | On your phone (app storage) |
| Steps, distance, active calories, heart rate, resting heart rate from your watch | Health Connect (written there by apps such as Zepp), **only if you turn on "Show Amazfit watch data" and grant access** | To show your watch's data and estimate a weekly activity score | Read and shown on your phone; not copied elsewhere |
| Steps and distance counted by the phone | The app | Written to Health Connect **only if you turn on "Share steps with Google Health" and grant access**, so apps you choose (such as Google Health) can show them | Health Connect on your phone |

## Health Connect

Pedometer's use of information received from Health Connect adheres to the [Health Connect Permissions policy](https://support.google.com/googleplay/android-developer/answer/12991134), including the Limited Use requirements. The app:

- reads only the data types listed above, and only after you grant permission;
- uses that data only to show it to you in the app and calculate the activity score;
- does not sell, share or transfer Health Connect data, and does not use it for advertising.

You can withdraw Health Connect access at any time in Android Settings › Health Connect › App permissions › Pedometer, or by turning the features off in the app's Settings.

## Permissions

| Permission | Why |
|------------|-----|
| Physical activity | Use the phone's step sensor chips |
| Notifications | Show today's steps while counting, and a step-goal notice |
| Foreground service (health) | Keep counting while the app is closed |
| Start at boot | Resume counting after the phone restarts, if counting was on |
| Health Connect read/write (optional) | As described above |

## Backups

Android may include the app's own storage (your settings and history) in your device backup to your Google account, if device backup is turned on. This is controlled by Android, not by the app.

## Deleting your data

- Reset today's count from the app's menu.
- Delete everything by uninstalling the app or clearing its storage (Android Settings › Apps › Pedometer › Storage › Clear storage).
- Data the app wrote to Health Connect can be deleted in Health Connect (Android Settings › Health Connect › Data and access).

## Children

The app is not directed at children under 13.

## Medical disclaimer

Pedometer is for general fitness only. Its step counts, calorie estimates and activity score are estimates and are not medical advice. The activity score is the app's own estimate and is not Zepp's PAI.

## Changes and contact

Changes to this policy will be published at this address. Questions: open an issue at <https://github.com/markybadz/android-pedometer-app/issues>.

The app's source code is available at <https://github.com/markybadz/android-pedometer-app> under the GNU General Public License v3.
