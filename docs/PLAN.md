# FocusLite: master plan

Personal clone of **SocialLite** (sociallite.app), Android first. The app is now named **Regain** (see README for status).
Prepared 2026-11-22. Status: **plan only, nothing built yet.**

> Working name "FocusLite". Rename any time; the plan does not depend on it.

---

## 1. Context

SocialLite removes Reels, Shorts, Spotlight and Explore from social apps but keeps DMs.
It also has daily short-video allowances, a timed "Post Mode" unlock, focus timers and a three-phase Sleep Mode.
It costs $9.99/month or $29.99/year and has no free tier.

Parth wants his own version, for personal use first, built so it can scale later.
It should match SocialLite's features and add better ones taken from competitors.

**Target device:** Parth's iQOO Z6 5G (Funtouch OS, no root). ADB is already at `D:\App data\platform-tools\adb.exe`.

**Labels used in this plan:**
- **reported:** a claim taken from someone else's website or docs.
- **measured:** checked on this PC in this session.
- **interpretation:** my own design choice.

---

## 2. What we learned (research summary)

### 2.1 SocialLite (reported, from sociallite.app)

**How it blocks**
- **Android:** an `AccessibilityService` detects the Reels, Shorts and Spotlight screens inside the native apps and blocks them. Detection runs locally.
- **iOS:** in-app web views of Instagram and YouTube, with CSS hiding the short-form sections.

**Features**
- Feed removal, always on: Reels, Shorts, Explore, suggested posts and ads.
- Daily allowance, counted in videos or minutes, reset at midnight.
- Post Mode: unlocks the native app for N minutes so you can post, then re-locks.
- Focus timer.
- Sleep Mode with three phases:
  - Wind-down: apps locked before bed.
  - Overnight: everything locked except essentials (Phone, Messages, alarm).
  - Morning: apps stay locked for N minutes after waking.
- Usage stats: time per app today and a weekly view.
- Parent plan: link a child's phone with a code, with remote rules, downtime and per-app limits.
- Chrome extension (announced "coming soon"): feed controls, site blocking, focus sessions.

**What SocialLite does not have:** streaks, scores, friend accountability, friction pauses, remote detection-rule updates, or tamper alerts. Those are our room to do better.

### 2.2 Competitors (reported; full matrix in `research/competitors.md` in Phase 1)

| App | Idea worth taking |
|---|---|
| one sec / ScreenZen | Breathing or delay pause before opening, instead of a hard block. A cited study found a 57% drop in usage. |
| Opal / Freedom | Hard mode: a Deep Focus session that cannot be cancelled. |
| Clearspace | Earn screen time with push-ups or steps. A "write why" note to unlock. |
| Brick | Unlock only by tapping a physical NFC tag. |
| Jomo / BePresent | Screen-time budgets, squads, group sessions, weekly reports. |
| DetoxDroid | Emergency Brake. Greyscale on short-video screens. Escalating pause cost. |
| Curbox / Scrolless | Allow a reel opened from a DM, block swiping onward. Count reels, not only minutes. |
| Decafly | Push new detection rules without shipping an app update. |
| Nudge | When detection fails, dump the screen's view IDs so it can be fixed fast. |
| Forest | A visible garden that grows on clean days. |

**Most common complaints across all of them:**
1. It stops working after an app update or because the phone kills it to save battery.
2. It is easy to bypass.
3. Billing surprises.

Our design targets complaints 1 and 2 directly. Complaint 3 does not apply to a personal app.

### 2.3 Open-source references (measured, `gh api` on 2026-11-22)

| Repo | Stars | Licence | What we use it for |
|---|---|---|---|
| curbox-app/curbox-android | 1,569 | GPL-3.0 | Per-app detection config (`ReelAppConfig.kt`), DM-reel exception |
| flxapps/DetoxDroid | 567 | GPL-3.0 | `res/xml/scroll_surfaces.xml` ID list, scroll-physics fallback |
| duartebarbosadev/Scrolless | 78 | GPL-3.0 | Reply-button matcher, live overlay timer, interval limits |
| astraedus/nudge | 63 | GPL-3.0 | Diagnostic dump of view IDs |
| akaMrNagar/Mindful (reported) | — | — | Flutter UI with a Kotlin service, browser URL-bar detection |

**Licence rule (interpretation):**
- Read these repos for ideas, and reuse **view IDs**, which are facts rather than code.
- Write our own code. Copying GPL code would make FocusLite GPL. That is fine for personal use but limits selling it later.
- Never copy SocialLite's decompiled code, strings or assets. The `rea` disclaimer says the same.

### 2.4 Known view IDs (reported; change with app updates, verify on device in Phase 1)

| App | Package | Short-video surface | Keep-alive / DM markers |
|---|---|---|---|
| Instagram | `com.instagram.android` | `clips_viewer_view_pager` or `clips_view_pager`, plus a Reel control on screen (`clips_ufi_component`, `clips_author_username`, `clips_video_container`). Tabs: `clips_tab`, `search_tab`. | DMs: `direct_thread_header`, `row_thread_composer_edittext`, `direct_inbox_recycler_view`. Reel opened from a DM: `clips_sent_by`, `reel_viewer_message_composer`, `reply_bar_container_scroll_view`. |
| YouTube | `com.google.android.youtube` | `reel_recycler`, `reel_player_page_container`, `reel_watch_player`, `reel_watch_fragment_root`. Shorts tab: `pivot_bar` button. | — |
| Snapchat | `com.snapchat.android` | `spotlight_container`, `spotlight_pager`, `spotlight_fullscreen_player`. Discover: `df_large_story`. | Chat and Stories allowed |
| TikTok | `com.zhiliaoapp.musically`, `com.ss.android.ugc.trill` | IDs are scrambled (for example `id/i98`), so block the **whole app** or apply a time budget. | — |
| Browsers | Chrome and others | Read the URL bar and match `instagram.com/reels/`, `youtube.com/shorts/`, `snapchat.com/spotlight/` | — |

### 2.5 Platform constraints (reported)

- **Restricted settings (Android 13+, sideloaded apps):** the first attempt to enable accessibility is blocked. Then go to App info → ⋮ → *Allow restricted settings*. The onboarding must walk through this.
- **Android 17 Advanced Protection Mode:** removes accessibility access from non-accessibility-tool apps. Keep it **off** on this phone.
- **Play Store:** must not set `isAccessibilityTool="true"`. Needs a prominent in-app disclosure and a Play Console declaration. This only matters if it is ever published.
- **Battery killers (Vivo/iQOO):** needs autostart enabled, battery set to "No restrictions", and the app locked in recents. Build a setup wizard and a watchdog.
- **iOS:** the Screen Time API can only shield **whole apps**. It cannot hide Reels inside Instagram. The family-controls entitlement needs a paid developer account ($99/yr). The web-view wrapper is fragile, and Google sign-in is blocked in web views. **iOS is deferred.**

### 2.6 This PC (measured 2026-11-22)

| Item | Status |
|---|---|
| Android SDK | `%LOCALAPPDATA%\Android\Sdk`: platforms 36 and 36.1, build-tools 35.0.0 and 36.1.0. OK. |
| ADB | 1.0.41 / 37.0.1 at `D:\App data\platform-tools`. Phone not connected during the check. |
| JDK | No `java` on PATH, but Android Studio's bundled JDK 21 is at `E:\Android Studio\jbr` and is used for builds (`JAVA_HOME`). jadx may still need a full JDK 17+. |
| Android Studio | **Installed** at `E:\Android Studio` (build 253.29346.138, Gradle API 9.0). Corrected 2026-11-22; the first check missed it. |
| Node | v22.13.0. **Too old for rea**, which needs ≥22.23.4 or 24.22.1+. |
| jadx / apktool | Missing. rea needs `jadx-headless-mcp-0.8.0-all.jar` and apktool ≥3.0.2. |

---

## 3. Product scope

### 3.1 Parity with SocialLite (MVP + v1)

| # | Feature | Phase |
|---|---|---|
| P1 | Block IG Reels tab and feed, IG Explore, YT Shorts, Snap Spotlight and Discover; block TikTok as a whole app | MVP |
| P2 | Keep DMs working; allow a reel opened from a DM but block swiping onward | MVP |
| P3 | Block screen (overlay) with "Go to DMs / Home" buttons, or auto-Back | MVP |
| P4 | Daily allowance per surface: N videos or N minutes, reset at midnight | v1 |
| P5 | Post Mode: unlock surfaces for 5/10/15 minutes, then re-lock | v1 |
| P6 | Focus timer: pause chosen apps for a duration (foreground service + notification) | v1 |
| P7 | Sleep Mode: wind-down / overnight (essentials only) / morning lock, on a daily schedule | v1 |
| P8 | Usage dashboard: time per app today, weekly chart, reels counted, time saved | v1 |
| P9 | Browser blocking: URL-bar match for /reels, /shorts, /spotlight in Chrome | v1 |
| P10 | Chrome desktop extension (MV3): hide Shorts and Reels, block URLs, focus sessions | v2 |

### 3.2 New features, beyond SocialLite

| # | Feature | Inspired by | Phase |
|---|---|---|---|
| N1 | **Remote rules file**: detection IDs live in versioned JSON (bundled, plus optional URL fetch), so a fix ships without rebuilding | Decafly | MVP |
| N2 | **Diagnostics screen**: one tap dumps the current screen's view IDs and tree to a file, to fix broken detection fast | Nudge | MVP |
| N3 | **Setup health check**: accessibility on, battery unrestricted, autostart on, usage access granted, with a red/green list | AppBlock complaints | MVP |
| N4 | **Pause screen**: 5–10 s of breathing before Reels unlock, instead of a hard block (per-surface choice) | one sec | v1 |
| N5 | **Escalating unlock cost**: each extra unlock in a day waits longer (10 s → 30 s → 60 s …) | DetoxDroid, Jomo | v1 |
| N6 | **"Write why" unlock**: type a reason to unlock; reasons are logged for later review | Clearspace | v1 |
| N7 | **Live overlay timer** while on an allowed short-video surface | Scrolless | v1 |
| N8 | **Greyscale or blur** on short-video surfaces instead of blocking | DetoxDroid | v1 |
| N9 | **Streaks + Focus Score**: a day is "clean" if allowances are kept; score from usage vs goals | Opal, BePresent | v1 |
| N10 | **Weekly report**: reels avoided, time saved, top unlock reasons, trend | Jomo, Opal | v1 |
| N11 | **Strict mode / Emergency Brake**: settings locked during a session; ending early needs a 10-minute wait | Opal Deep Focus, DetoxDroid | v1 |
| N12 | **Tamper alerts**: notify (and log) when accessibility is turned off, the clock changes, or the service is killed | Opal/Freedom complaints | v1 |
| N13 | **Interval budgets**: for example 10 minutes allowed per 2 hours | Scrolless | v2 |
| N14 | **NFC tag unlock**: any NFC sticker becomes the key for strict sessions | Brick | v2 |
| N15 | **Earn minutes with exercise**: steps from Health Connect buy allowance | Clearspace | v2 |
| N16 | **Device admin uninstall guard** during strict sessions | Lock Me Out, Mindful | v2 |
| N17 | **Garden or streak visual** that grows on clean days | Forest | v2 |
| N18 | **Accountability buddy**: a partner gets the weekly report or must approve a strict-mode exit (needs backend) | BePresent, SocialLite parent plan | v3 |
| N19 | **Minimal text-only launcher mode** | Olauncher | v3 |
| N20 | **Feed for Parth's brain**: export the daily summary as JSON for the brain's journal and stats (local file, no cloud) | own idea | v2 |

---

## 4. Architecture

### 4.1 Stack (interpretation; chosen for this project)

- **Language/UI:** Kotlin 2.x + Jetpack Compose (Material 3).
  - All the hard parts (accessibility service, overlay, usage stats, alarms) are native Android.
  - A single language avoids Flutter or React Native bridges.
- **Architecture:** clean, multi-module, following the "Now in Android" pattern (Google's reference app).
- **DI:** Hilt. **Persistence:** Room (history) + Proto/Preferences DataStore (settings). **Async:** Coroutines + Flow.
- **Background:**
  - Foreground service (`specialUse`) for focus sessions.
  - `AlarmManager.setExactAndAllowWhileIdle` for Sleep phases.
  - WorkManager for the daily rollup and the rules refresh.
- **Serialization:** kotlinx.serialization (rules JSON). **Charts:** Vico (Compose charts).
- **Build:** Gradle Kotlin DSL, version catalog (`libs.versions.toml`), convention plugins in `build-logic/`.
- **Quality:** ktlint/detekt, JUnit5 + Turbine for unit tests, Robolectric for service logic, Compose UI tests.
- **Min SDK 28, target SDK 36.**

### 4.2 Monorepo layout (`E:\Tools\FocusLite`)

```
FocusLite/
├─ docs/                    PLAN.md (this), ARCHITECTURE.md, adr/0001-*.md, SETUP.md
├─ research/                sociallite-teardown.md, competitors.md, view-id-log.md
│  └─ apk/                  ⚠ git-ignored: pulled APKs and decompiled output
├─ rules/                   platform-rules.json (+ schema.json, CHANGELOG.md)
├─ android/                 Gradle project
│  ├─ app/                  Application, MainActivity, nav graph, Hilt entry
│  ├─ build-logic/          convention plugins (android-library, compose, hilt, feature)
│  ├─ core/
│  │  ├─ model/             pure Kotlin data classes (Platform, Surface, Policy, Session…)
│  │  ├─ common/            dispatchers, clock abstraction, result types
│  │  ├─ database/          Room: entities, DAOs, migrations
│  │  ├─ datastore/         settings (Proto DataStore)
│  │  ├─ data/              repositories (Rules, Usage, Allowance, Focus, Sleep, Streak)
│  │  ├─ designsystem/      theme, tokens, components (FLButton, FLCard, StatTile…)
│  │  ├─ ui/                shared UI pieces (permission cards, charts)
│  │  └─ testing/           fakes, fixture loaders, test rules
│  ├─ engine/
│  │  ├─ detection/         ★ pure Kotlin: ScreenSnapshot → Surface (rules-driven)
│  │  ├─ policy/            ★ pure Kotlin: (Surface, State) → Decision
│  │  ├─ accessibility/     FocusAccessibilityService, snapshot builder, event throttle
│  │  ├─ enforcement/       overlay windows, back/redirect actions, greyscale
│  │  ├─ scheduler/         focus FGS, sleep alarms, boot receiver, watchdog
│  │  └─ usage/             UsageStatsManager reader, session pairing, reel counter
│  └─ feature/
│     ├─ onboarding/        disclosure, restricted-settings guide, OEM battery wizard
│     ├─ home/              today card, quick actions (Post Mode, Focus)
│     ├─ blocking/          per-platform surface toggles, allowances, pause style
│     ├─ focus/             timer UI, strict mode
│     ├─ sleep/             three-phase schedule editor
│     ├─ stats/             charts, weekly report, streaks
│     ├─ settings/          rules source, tamper alerts, export
│     └─ diagnostics/       view-ID dump, live detection log, health check
├─ extension-chrome/        (v2) MV3: content scripts + declarativeNetRequest
├─ backend/                 (v3, optional) accountability and sync, Supabase or Ktor
├─ ios/                     (later) Screen Time + Safari extension; needs a Mac
└─ tools/                   pull-apk.ps1, dump-ids.ps1, rea setup notes
```

**Why this scales (interpretation):**
- The two ★ engine modules are pure Kotlin with no Android imports, so they are unit-tested with recorded screen fixtures.
- The same rules and policy can later be reused (Kotlin Multiplatform) by an iOS or desktop target.
- Features depend only on `core:*`, never on each other. A new feature is a new module.
- Rules are data, not code, so app updates on Instagram's side are fixed by editing JSON.

### 4.3 Runtime flow

```
AccessibilityEvent (window state change / content change / scroll; filtered to watched packages)
   → Throttle (300 ms debounce, latest-wins, off main thread)
   → SnapshotBuilder: walks the window and collects view IDs, content descriptions, the selected tab
     and, for browsers, the URL bar text  → ScreenSnapshot
   → Detector (rules JSON): Surface = REELS_FEED | REEL_FROM_DM | SHORTS | SPOTLIGHT | EXPLORE
     | DM | BROWSER_SHORT_URL | OTHER
   → PolicyEngine (state: allowances used, focus session, sleep phase, post-mode window, strict mode)
       → Decision = Allow | Count(+1) | Pause(seconds) | Overlay(kind) | Back | RedirectTab(id) | Greyscale
   → Enforcer executes; UsageRecorder logs (surface, start, end, count) to Room
```

**Rules JSON shape** (as built; ids are short names, the first matching rule wins, so specific rules go first; real file: `rules/platform-rules.json`, with a per-rule `verified` flag that Phase 1 flips):

```json
{
  "version": 3,
  "updated": "2026-11-22",
  "apps": [{
    "package": "com.instagram.android",
    "minVersion": null,
    "surfaces": [
      {"surface": "REEL_FROM_DM", "allOf": ["clips_viewer_view_pager"],
       "anyOf": ["clips_sent_by","reel_viewer_message_composer","reply_bar_container_scroll_view"]},
      {"surface": "REELS_FEED", "allOf": ["clips_viewer_view_pager"],
       "anyOf": ["clips_ufi_component","clips_author_username","clips_video_container"]},
      {"surface": "DM", "anyOf": ["direct_thread_header","direct_inbox_recycler_view"]}
    ],
    "escapeTab": "direct_tab"
  }]
}
```

### 4.4 Data model (Room)

| Entity | Fields |
|---|---|
| `SurfaceRule` | platform, surface, mode (BLOCK / ALLOWANCE / PAUSE / GREYSCALE / ALLOW), allowanceCount, allowanceMinutes |
| `UsageSession` | package, surface, startedAt, endedAt, videosSeen |
| `DailyTotal` | date, package, foregroundMs, shortVideoMs, videosSeen, unlocks, cleanDay |
| `UnlockEvent` | at, surface, method (PAUSE / POST_MODE / WHY / NFC / EMERGENCY), reason text, cost in seconds |
| `FocusSession` | startedAt, plannedMin, endedAt, strict, endedEarly |
| `SleepSchedule` | windDownStart, bedtime, wake, morningLockMin, essentials[] |
| `TamperEvent` | at, type (A11Y_OFF / CLOCK_CHANGE / SERVICE_KILLED / UNINSTALL_ATTEMPT) |

### 4.5 Design system (applying the ui-ux-pro-max rules)

- **Style:** calm, minimal, premium.
  - Material 3 with a custom tonal palette: a deep teal or ink primary, warm neutral surfaces, one accent for "clean day".
  - Light and dark themes are designed together, with semantic tokens only (no raw hex in screens).
- **Type:** Material type roles. Body 16sp. Tabular figures for timers and counts.
- **Navigation:** a bottom bar with 4 items (Home · Focus · Sleep · Stats). Settings and Diagnostics sit behind the Home top bar.
- **Touch and accessibility:**
  - Touch targets ≥48dp. Contrast ≥4.5:1 in both themes. Respect system font scale and reduced motion.
  - Icons from one set (Material Symbols Rounded). No emoji as icons.
- **Motion:** 150–300 ms, ease-out on enter. The block overlay fades in with no jarring flash.
- **Block screen:**
  - One primary action ("Back to DMs") and a quiet secondary ("Unlock for 5 min").
  - It shows today's count and the current streak, so blocking also motivates.
- **Note:** the ui-ux-pro-max search database is empty in this install (its `scripts/` and `data/` folders are empty). The quick-reference rules were applied by hand. Run its design-system generator once it is fixed.

---

## 5. Phased roadmap

Order is fixed. Each phase ends with something working on the phone.

### Phase 0: Tooling and repo (≈½ day)
1. ~~Install JDK 17 and Android Studio.~~ **Done:** Android Studio is installed; builds use its bundled JDK 21 via `JAVA_HOME=E:\Android Studio\jbr`. A separate JDK 17 is only needed if jadx refuses the bundled one.
2. Upgrade **Node to 24 LTS** (≥24.22.1) for rea. Check that the other `E:\Tools` projects, such as `aero`, still build.
3. Set up rea: `npx rea-agents setup`, or `/plugin marketplace add morluto/rea` then `/plugin install rea@rea`. Use one or the other, not both.
4. Install jadx (`jadx-headless-mcp-0.8.0-all.jar`, check its SHA-256) and apktool ≥3.0.2.
5. Set `REA_JADX_MCP_JAR`, `JAVA_HOME`, `REA_APKTOOL_COMMAND` and `REA_ADB_PATH=D:\App data\platform-tools\adb.exe`. Run `rea doctor --provider jadx --json`.
6. Create the monorepo skeleton (§4.2): `git init`, `.gitignore` covering `research/apk/`, `*.apk` and `build/`.
   - **No commits or pushes unless Parth asks.**
   - Decide later whether to make a **private** GitHub remote.

### Phase 1: Teardown and evidence (≈1 day)
1. Install SocialLite from the Play Store on the iQOO (free download). Do not subscribe unless Parth decides to.
2. Pull the APK split set from the phone (read-only on the device):
   - `rea list-adb-devices`
   - `rea pull-adb-package <serial> com.sociallite.android --output-directory E:\Tools\FocusLite\research\apk`
3. Analyse the APK:
   - `rea inspect-android-package <base.apk>`: permissions, services, receivers, foreground service types.
   - Accessibility config XML: `apktool d`, then read `res/xml/*`.
   - `rea identify-flutter-build` and `project-android-application-graph`: native Kotlin, Flutter or React Native?
   - Search classes for `AccessibilityService`, `clips_`, `reel_`, `spotlight`, and remote-config or rules URLs.
4. Observe it running:
   - `rea inspect-adb-window` while on Reels.
   - `read-adb-logcat`.
   - Note how Post Mode and Sleep phases behave.
5. Capture **our own fixtures**: with a tiny debug dump script, record the view-ID trees of the Reels feed, a Reel from a DM, the DM inbox, YouTube Shorts and Snapchat Spotlight on *this phone's* app versions. Save them to `android/core/testing/fixtures/`.
6. Write `research/sociallite-teardown.md`:
   - **What they do**, kept separate from **what we will do differently**.
   - Label every claim measured or interpretation.
   - Learn behaviour and architecture only. Copy no code.

### Phase 2: MVP, blocking that works (≈3–5 days)
1. `core:model` and `engine:detection` with rules JSON v1 (IG, YT, Snap, TikTok, Chrome). Unit tests against the Phase 1 fixtures.
2. `engine:policy`, MVP rules: always block, plus a DM-reel exception.
3. `engine:accessibility`:
   - Service config: `typeWindowStateChanged|typeWindowContentChanged|typeViewScrolled`, `flagReportViewIds|flagIncludeNotImportantViews`, `canRetrieveWindowContent=true`, `packageNames` filter.
   - Debounce and snapshot builder; use `findAccessibilityNodeInfosByViewId` first.
4. `engine:enforcement`: block overlay (`TYPE_ACCESSIBILITY_OVERLAY`, no overlay permission needed), Back action with a 1 s cooldown, tab redirect.
5. `feature:onboarding`:
   - Prominent disclosure screen.
   - Accessibility enable flow, including the *Allow restricted settings* walkthrough.
   - Vivo/iQOO battery and autostart wizard.
6. `feature:diagnostics`: health check (N3), view-ID dump (N2), live detection log.
7. `feature:home`, minimal: on/off state and blocks today.
8. Sideload a debug build via `adb install`, then daily-drive it for 2–3 days.

### Phase 3: SocialLite parity (≈1–1.5 weeks)
P4 allowances → P5 Post Mode → P6 focus timer (foreground service) → P7 Sleep three phases (exact alarms + boot receiver) → P8 usage dashboard (UsageStats `queryEvents` pairing + reel counter, Vico charts) → P9 browser URL matching → N1 remote rules refresh (WorkManager, hash check).

### Phase 4: The upgrades (≈1–2 weeks)
N4 pause screen → N5 escalating cost → N6 write-why → N7 live timer → N8 greyscale → N9 streaks + Focus Score → N10 weekly report → N11 strict mode → N12 tamper alerts → N20 brain export.

### Phase 5: Desktop (≈2–3 days)
`extension-chrome/`:
- MV3 content script (CSS `ytd-reel-shelf-renderer`, `ytm-shorts-lockup-view-model*`, `a[href^="/shorts/"]`, `a[href="/reels/"]`, plus a MutationObserver).
- `declarativeNetRequest` block for `/reels/`, plus a JS redirect for SPA navigation (`yt-navigate-finish`).
- It shares `rules/` selectors.

### Phase 6: Scale-out (only if wanted)
- v2 features: N13–N17.
- Backend for N18: Supabase (auth, Postgres, edge functions) or a Ktor server.
- KMP-extract `engine:detection` and `engine:policy`.
- iOS app: Screen Time shields + Safari Web Extension; needs a Mac, Xcode and the $99/yr developer account.
- If it is ever published:
  - Play policy work (disclosure, declaration video).
  - A privacy policy.
  - Replace any GPL-derived code.

---

## 6. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Instagram or YouTube update renames view IDs | Rules JSON (N1) + diagnostics dump (N2) + fixture tests. Fix by editing JSON, not code. |
| Vivo/iQOO kills the service | Onboarding wizard + health check + watchdog alarm + tamper alert when the service stops. |
| Battery drain from tree walks | Package filter, 300 ms debounce, ID lookup before a full walk, no scroll-event scans except for counting. |
| Bypass by me (turn it off) | Strict mode, escalating cost, tamper log, optional device admin, accountability buddy (v3). |
| Restricted settings blocks a sideload | Documented walkthrough in onboarding. Long term: Play internal-testing track. |
| Android 17 Advanced Protection | Keep it off. The app detects it and warns. |
| Legal | Personal use; copy no SocialLite code or assets; learn from GPL repos without pasting code. |

---

## 7. Verification

- **Unit:** `engine:detection` and `engine:policy` against recorded fixtures for each surface, plus "app updated, ID missing" cases. Run `./gradlew test`.
- **Service:** Robolectric tests for throttle and enforcement decisions.
- **On device (manual matrix, iQOO):**

| Action | Expected |
|---|---|
| Open IG Reels tab | Blocked |
| Open a reel sent in a DM | Plays; swiping onward is blocked |
| Use DMs | Work |
| Open YT Shorts tab or a Shorts link | Blocked |
| Open Snap Spotlight | Blocked; chat works |
| Open TikTok | Blocked or budgeted |
| Visit youtube.com/shorts in Chrome | Blocked |
| Run Post Mode | Unlocks, then re-locks on time |
| Cross a Sleep phase boundary | Correct phase applies |
| Reboot the phone | Schedules restored |

- **Endurance:** run 48 h; check `adb shell dumpsys batterystats` for the app, and confirm the service survives a night.
- **Logs:** `adb logcat` tag `FocusLite`; the diagnostics screen shows a detection hit rate.

---

## 8. Open decisions for Parth
1. **Name:** keep "FocusLite" or pick another?
2. **iQOO Android version:** check *Settings → About*. This sets min/target SDK testing.
3. **Ever sell or publish it?** If yes, write everything clean-room from day 1, with no GPL code.
4. **Subscribe to SocialLite for the teardown?** Only needed to observe paid-only features like Post Mode and Sleep.
5. **Private GitHub backup?** Default is no remote, no commits until asked.
