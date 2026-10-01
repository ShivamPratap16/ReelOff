# ReelOff

**Use your apps. Skip the scroll.**

ReelOff is an Android app that removes the addictive parts of social apps, such as YouTube Shorts,
Instagram Reels, Facebook Reels, Snapchat Spotlight, TikTok and endless feeds. YouTube,
Instagram and Facebook stay installed and usable for messages, search, subscriptions and
long-form videos.

See [docs/MARKET_RESEARCH.md](docs/MARKET_RESEARCH.md) for the competitive analysis behind the design.

## Features

- **Short-video blocking** for YouTube Shorts (including ReVanced builds), Instagram Reels,
  Facebook Reels, Snapchat Spotlight, TikTok, and Moj/Josh/MX TakaTak/Chingari. It also blocks
  Shorts/Reels/TikTok links in 16 browsers.
- **Precise detection, no feed bouncing.** The Shorts shelf and inline reels on home feeds are
  left alone. Only the visible, full-screen player or the selected Shorts/Reels tab triggers a block.
- **Gentle exit.** ReelOff presses Back for you. It falls back to Home only if Back keeps
  reopening the player.
- **Daily allowance (optional).** For example, 10 minutes of Shorts a day, then blocked until midnight.
- **Endless-scroll limiter.** A swipe budget per sitting for Instagram, X, Reddit, Facebook,
  Threads, LinkedIn and more, then a forced break.
- **Commitment timer.** Pausing protection or loosening any setting means waiting through a
  countdown (10 s to 5 min). Making settings stricter is instant. Pauses end on their own.
- **Stats.** Blocks today, a 7-day chart, and an estimate of the time you got back.
- **Private by design.** The app has no internet permission and no accounts. It only inspects
  the apps you choose and never reads message text.

## Install

1. Download `ReelOff.apk` from the latest successful
   [Android CI run](../../actions/workflows/android.yml) (artifact **ReelOff-apk**), or from
   **Releases** if you push a `v*` tag.
2. Open the APK on your phone and allow installing from that source.
3. Open ReelOff, read the disclosure, tap **Agree and continue**, then go to
   **Accessibility → Installed apps → ReelOff** and turn it on.
4. **"App not installed to protect you" / "App blocked to protect your device"**: Google Play
   Protect blocks apps that ask for Accessibility when they are installed from outside the Play
   Store. This is strictest in India. To get around it, do one of these:
   - In the Play Store, tap your profile picture, then **Play Protect → ⚙ Settings**. Turn off
     **Scan apps with Play Protect**, install the APK, then turn scanning back on.
   - Or install from a computer: `adb install ReelOff.apk`.
   - Permanent fix: publish to a Google Play internal-testing track and install from there.
5. If the toggle is greyed out (Android 13+ "restricted setting"), go to
   **App info → ⋮ → Allow restricted settings** and try again. The app links you there.

## Architecture

```
core/   Pure Kotlin (JVM). All decision logic, unit-tested without a device.
  ScreenSnapshot    plain-data copy of the screen (view ids, labels, visibility, size)
  BlockRule         a distraction's signals; visible + ≥70%-of-screen check
  RuleCatalog       every supported app and view id (edit here when an app redesigns)
  UrlMatcher        host/path matching for browser address bars
  Policies          AllowanceTracker, ScrollLimiter, EscalationGuard, PauseState
  BlockEngine       snapshot/scroll event → Decision(Back | Home | allow | none)
app/    Android (Kotlin, Jetpack Compose, Material 3)
  service/ReelOffAccessibilityService   event filtering, throttling, actions
  service/SnapshotBuilder               AccessibilityNodeInfo tree → ScreenSnapshot
  data/Settings                         SharedPreferences settings and stats
  ui/                                   disclosure, dashboard, dialogs
```

## Build

Requirements: JDK 17+ and the Android SDK (API 35).

```bash
./gradlew :core:test            # detection engine unit tests (no Android SDK needed)
./gradlew :app:assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions (`.github/workflows/android.yml`) runs the tests, builds the debug and release
APKs, runs Android lint, and uploads the APKs as artifacts on every push.

## When an app update breaks detection

Apps rename views from time to time. To find the new id, open the screen and run:

```bash
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml
grep -o 'resource-id="[^"]*"' ui.xml | sort -u
```

Add the new id to `core/src/main/kotlin/app/reeloff/core/RuleCatalog.kt`, then add a test.

## Publishing on Google Play

- Replace the debug signing config in `app/build.gradle.kts` with your own keystore.
- Fill in the Accessibility API declaration in Play Console. Describe the same use the in-app
  disclosure describes.
- Privacy policy: no data is collected or shared.
