# Regain

Personal Android app (inspired by SocialLite) that removes Reels, Shorts and Spotlight but keeps DMs, with a pause screen, daily limits, Post Mode, focus sessions, a sleep routine, streaks and real screen-time stats. Icon: `branding/regain-icon-1024.png` (made by `tools/make_icon.py`). Plan: `docs/PLAN.md`, UI notes: `docs/UI-SPEC.md`.

The folder, the code package and the Android application id still say "focuslite" on purpose: renaming the application id would make Android treat it as a new app and drop the accessibility, usage and battery permissions. Rename before any publishing.

No git commits unless Parth asks. **No mock data:** every number comes from the phone (block log, Android usage stats, the app's own logs); without a permission the app says so and shows nothing.

## Status (2026-11-22)
Full feature list with status: `docs/FEATURES.md`.
Built, with unit tests (64 pass: detection, policy, sleep, usage, sessions, reel counter):
- Blocking with per-surface modes: Block, Pause first, Daily limit (minutes), Allow. Instagram Reels, YouTube Shorts, Snapchat Spotlight, TikTok (whole app), short-video links in browsers.
- Pause screen: breathing ring, wait of 10/20/40/60 s that grows with each unlock, a reason, then 5 minutes unlocked with a live countdown pill. Reasons are logged.
- Post Mode, Focus session (strict = cannot end early), Sleep routine (wind-down, overnight, morning), daily goal and streak.
- Screen Time tab: tap-a-day week chart with goal line, ring, opens, longest stretch, first/last use, hour-by-hour chart, per-app rows with real icons, weekly report.
- Reel counter: one per swipe, only for reels you were allowed to watch; shown on Home, Screen Time, the countdown pill and the export. Tested on the phone for Instagram Reels (1 on landing, +1 per swipe: 1, 2, 3, 4). YouTube Shorts and Snapchat Spotlight not tested.
- Live counter pill: a white pill with a reel glyph and the number, on top of the video while you watch (shows the time left beside it during an unlock). Checked on the phone: 5 on landing, 6 after a swipe. Switch in Settings → Display.
- Protection history (gaps are logged by the service), daily JSON export for the brain, screen recorder for detection rules.

Checked live on the iQOO: Instagram Reels blocked, pause screen and reasons, unlock with the countdown pill, real icons and real usage numbers on Home and Screen Time.
NOT yet checked live: daily limit counting, Post Mode, Focus, Sleep, the pause countdown after the 20:20 fix, swiping on from a friend's reel. Only the Instagram Reels rule is `verified` (the Instagram tab ids were also seen in a recorded screen).

Not built: remote rule updates, greyscale mode, interval budgets, NFC unlock, exercise-earned minutes, uninstall guard, garden visual, accountability buddy, minimal launcher, allowance counted in videos (minutes only), Instagram Explore blocking, Chrome extension, iOS.

## Tools on this PC
- Android Studio: `E:\Android Studio` (bundled JDK 21 at `E:\Android Studio\jbr`)
- Android SDK: `%LOCALAPPDATA%\Android\Sdk` (path set in `android/local.properties`, git-ignored)
- ADB: `D:\App data\platform-tools\adb.exe`
- Gradle 8.14.3 (wrapper) + AGP 8.13.2 + Kotlin 2.3.21

## Commands (run in `android/`)
```powershell
$env:JAVA_HOME = "E:\Android Studio\jbr"
.\gradlew test                 # unit tests (pure Kotlin engine)
.\gradlew :app:assembleDebug   # build app/build/outputs/apk/debug/app-debug.apk
& "D:\App data\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

Record the screen that is open in 5 s (debug build, service on); files land in
`/sdcard/Android/data/app.focuslite/files/dumps/`:
```powershell
& "D:\App data\platform-tools\adb.exe" shell am broadcast -a app.focuslite.DUMP --el delay_ms 5000
& "D:\App data\platform-tools\adb.exe" pull /sdcard/Android/data/app.focuslite/files/dumps research\dumps
```
Dumps hold view IDs only, never text.
