# UI spec: modelled on SocialLite's layout

Sources viewed 2026-11-22 (reported): SocialLite's App Store marketing screenshots (iOS), its Google Play listing images, and sociallite.app. No Android in-app screens of the Sleep or Allowance pages were publicly visible, so those are designed from the feature descriptions.

## What SocialLite's UI looks like (reported)
- Light, high-contrast: white/near-white background, black ink, big bold geometric sans headings, soft grey rounded cards.
- **Home:** app title top-left, small pill top-right; a carousel of platform tiles with the selected one large and its neighbours peeking in; "<Platform> today / <time> / see details"; page dots; grey cards below (Settings, Post Blocker). Bottom nav of 3 icons: home, a circle (Screen Time), settings.
- **Screen Time:** "Screen Time" title, a progress ring ("77% locked in today"), "Last 7 days" black bar chart with day initials, "Today" total with a per-app list and progress bars.
- **Pick Apps:** hub diagram with the app logo in the middle and platform icons around it, "All connected", Manage Apps and Widget buttons, a Notifications row.
- **Per-platform sheet** ("Customize every aspect"): grouped switch rows, e.g. Blocking: Block Reels, Block Post Search; Pro Settings: Favorites, Block Ads, Block Suggested Posts.

## What FocusLite does (built)
Same structure and feel, with our own wording and artwork:
- Home: title + On/Off pill, platform carousel (Instagram, YouTube, Snapchat, TikTok, Browser), "<Platform> today / N blocked / see details", dots, info cards (protection state, Sleep Mode, Allowance and Post Mode, both marked "coming soon").
- Tapping a tile opens a bottom sheet with that platform's switches (stored in `PolicyPrefs`, read live by the service).
- Screen Time: ring (today vs the 7-day peak), last-7-days bars, today's blocks per surface.
- Settings: accessibility, battery and iQOO steps with a visible disclosure, plus the screen-recording tool.
- Light and dark themes, black-and-grey palette, 20-28 dp card corners.

## Deliberately not copied
- SocialLite's logo, wordmark, app icon, mascot or screenshots. Platform tiles are plain monograms.
- Their marketing copy. Identical visuals and text would be a trademark and copyright problem if the app is ever shared.

## Still to build (matches SocialLite's remaining screens)
Real time-on-app numbers (needs Usage access), Sleep Mode page, Allowance and Post Mode, a Pick Apps hub, widget, notification row, onboarding survey, Block Ads / Suggested Posts / Explore toggles.

## Design rules applied (2026-11-23)
Source: three public skill repos, read only (nothing installed or run): ceorkm/mobile-app-ui-design (licence unclear: README says MIT, no LICENSE file), jakubkrehel/skills (MIT), nextlevelbuilder/ui-ux-pro-max-skill (MIT). They are mostly web guidance, so the numbers were translated to Android by hand. No code or text was copied.

| Rule | What changed in Regain |
|---|---|
| Text contrast 4.5:1, chart marks 3:1 | Measured every pair. Light-mode green text was 3.5:1, now 5.6:1 (`#176B3A`). Unselected chart bars were about 1.9:1, now 3.1:1 light / 4.0:1 dark (`chartMuted`) |
| 48dp touch targets | Stepper buttons 40dp to 48dp; chips and switches already meet it |
| Tabular digits on changing numbers | Turned on for the whole type scale, so timers, totals and the counter keep their width |
| No text under 12sp | Chart labels 10sp to 12sp |
| Press feedback 0.96 over 150ms | Carousel icon tiles |
| Depth by hairline, not shadow | 1dp edge on cards and on app icons |
| One filled primary button per screen | Settings: only the first unfinished step is filled; Post Mode buttons outlined; Routines keeps "Start" as the one filled button |
| Switch and label as one target | New `SwitchRow` (Strict, Sleep mode, counter); haptic tick on toggles and steppers |
| Reduced motion | Pause screen keeps the ring still and changes the words when the phone's animations are off; counter pill skips its pop |
| Text description for charts | The ring has a spoken summary |

Not applied (needs testing or a decision): staggered entrance animation, skeleton loading, a 200%-font-scale pass, Instagram-icon inset outline in light mode, scripted checks from ui-ux-pro-max (its Python scripts were not audited, so not run).
Unchecked on the phone: haptics, press scale, reduced-motion behaviour.
