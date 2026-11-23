# Publishing Pedometer on Google Play

What's ready in the code, and what you need to do in the Play Console.

## Already done in the app (v2.3.0)

- [x] Own package name: `io.github.markybadz.pedometer` (the original author's `name.bagi.levente.pedometer` can't be used)
- [x] Targets Android 16 (API 36), as Google Play requires for new apps and updates
- [x] Edge-to-edge: every screen keeps clear of the status bar, navigation bar and camera cutout
- [x] Signed release bundle (`pedometer-release.aab`) built by GitHub Actions once the upload key is set up (below)
- [x] Privacy policy: [docs/PRIVACY.md](PRIVACY.md)
- [x] Health Connect privacy screen (required by Health Connect)
- [x] App icon, 512 × 512 icon and 1024 × 500 feature graphic: [docs/play/](play/)
- [x] No internet permission, no ads, no analytics

## 1. Set up the upload key (once)

You were given `upload-keystore.jks` and `SECRETS-README.txt`. Keep both private and backed up.

1. On GitHub: **Settings › Secrets and variables › Actions › New repository secret**, and add the four secrets listed in `SECRETS-README.txt`.
2. Run the build again (**Actions › Build APK › Run workflow**, or push any change).
3. The latest release now also has **`pedometer-release.aab`**. That's the file you upload to Play.

Never commit the key to the repository; `.gitignore` already blocks `*.jks`.

## 2. Google Play Console

1. **Developer account.** Create one at <https://play.google.com/console> (one-time fee) and complete identity verification.
2. **Create app.** Name "Pedometer" (or your own name), default language, App, Free.
3. **Play App Signing.** Accept it (default). Google keeps the app-signing key; your key above is only the *upload* key.
4. **Testing before production.** New personal developer accounts must run a closed test before they can publish to production. Check the current tester count and duration in the Play Console.
   - Release › Testing › Closed testing › Create track, upload `pedometer-release.aab`, add testers.

## 3. App content declarations (Policy › App content)

| Item | What to enter |
|------|---------------|
| Privacy policy | `https://github.com/markybadz/android-pedometer-app/blob/master/docs/PRIVACY.md` |
| Ads | No ads |
| App access | All features available without login |
| Content rating | Fill in the questionnaire (fitness/utility, no user content) |
| Target audience | 13+ (or 18+), not designed for children |
| Health apps declaration | Fitness / activity tracking |
| **Health Connect permissions** | Declare each permission and why: **Read steps, distance, active calories** to show the user's watch data; **read heart rate, resting heart rate** to estimate the weekly activity score; **write steps, distance** to share the phone's steps with apps the user chooses (Google Health). Link the privacy policy. Google reviews this before approval |
| **Foreground service: health** | Explain: counts steps continuously while the app is closed, using the step sensors; the user starts and stops it. Google may ask for a short video showing the feature |
| Data safety | See below |

### Data safety (suggested answers; check them against the current form)

- **Does the app collect or share user data?** The app has no internet access; data is processed on the device. Health Connect reads and writes happen on the device, at the user's request. Read Google's definitions of "collect" and "share" in the form, then answer accordingly. If unsure, declaring *Health and fitness: steps, heart rate* as **collected, processed on device only, optional** is the cautious choice.
- **Is data encrypted in transit?** Not applicable (no network).
- **Can users request deletion?** Yes: clear app storage, or delete in Health Connect.

## 4. Store listing (Grow › Store presence › Main store listing)

**App name:** Pedometer

**Short description (80 characters max):**
> Count steps, burn calories, see your Amazfit watch, and share with Google Health

**Full description:**
> Pedometer counts your steps with your phone's step sensor, and uses almost no battery.
>
> • Daily step goal ring, distance, pace, speed and calories
> • Calorie goal with progress
> • History: your last 30 days, with a 7-day summary
> • Amazfit watch: see your watch's steps, distance and calories (from the Zepp app, via Health Connect)
> • Weekly activity score estimated from your watch's heart rate (aim for 100+ a week)
> • Share your phone's steps with Google Health through Health Connect
> • Optional voice coaching to keep a steady pace
> • Keeps counting in the background and after a restart
>
> No ads. No account. No internet access: your data stays on your phone.
>
> The activity score is the app's own estimate and is not Zepp's PAI. Not a medical device.
>
> Open source (GPL-3.0): https://github.com/markybadz/android-pedometer-app
> Based on Pedometer by Levente Bagi.

**Graphics**

| Asset | File |
|-------|------|
| App icon 512 × 512 | `docs/play/icon-512.png` |
| Feature graphic 1024 × 500 | `docs/play/feature-graphic-1024x500.png` |
| Phone screenshots (2–8) | **Take real screenshots on your phone.** The images in `docs/screenshots/` are design previews with sample data and shouldn't be used as store screenshots |

**Category:** Health & Fitness. **Contact email:** required.

Mentioning Amazfit, Zepp and Google Health to describe compatibility is normally fine; don't use their logos.

## 5. Licence (GPL-3.0)

The original app is GPL-3.0, so your published version must be too: keep the source public (it is, on GitHub) and link to it in the description (done above).
