# Regain: what it has and what it does not have

Updated 2026-11-23. "Tested" means checked on the iQOO phone. "Unit tests" means the decision logic is tested on the PC only.

## Has

| Feature | Status |
|---|---|
| Block Instagram Reels (Reels tab and feed) | Built and tested on the phone |
| Block YouTube Shorts, Snapchat Spotlight, short-video links in browsers, whole TikTok app | Built. Rules copied from other projects, not tested on the phone |
| Keep DMs working; allow a reel a friend sent | Built. Swiping on from it is not tested |
| Four modes per surface: Block, Pause first, Daily limit, Allow | Built. Block and Pause first tested on the phone |
| Pause screen (breathing ring, wait 10/20/40/60 s, reason, 5-minute unlock) | Built and tested on the phone |
| Live countdown pill during an unlock or limit, stays up the whole visit | Built. Fix after the flicker report is not confirmed on the phone |
| Daily limit in minutes | Built. Unit tests only |
| **Reel counter** (reels watched, one per swipe, never counts blocked reels) | Built. Tested on the phone for Instagram Reels (1 on landing, then +1 per swipe). YouTube Shorts and Snapchat not tested |
| Live counter pill on top of the video (white pill, reel glyph, number pops on each reel; time left shown beside it during an unlock; on/off switch in Settings) | Built and tested on the phone for Instagram Reels |
| Post Mode (5/10/15 minutes without short-video rules) | Built. Unit tests only |
| Focus session (locks the social apps, strict = cannot end early) | Built. Unit tests only |
| Sleep mode (wind-down, overnight lock, morning lock) | Built. Unit tests only |
| Daily goal and streak | Built and shown on the phone |
| Screen Time: real time per app, 7-day chart you can tap, ring, opens, longest stretch, first and last use, hourly chart, per-app rows with real icons, weekly report | Built and tested on the phone |
| Real app icons on the tiles | Built and tested on the phone |
| Protection history (times the service was off) | Built and tested on the phone |
| Daily JSON export for your brain (time, blocks, reels, unlock reasons) | Built. Not run on the phone |
| Record a screen (view IDs only) to fix detection rules | Built and tested on the phone |
| Dark and light theme, setup screen with permissions and iQOO battery steps | Built and tested on the phone |
| Name Regain and the eclipse icon | Built. Icon on the home screen not checked |

## Does not have yet

| Feature | Note |
|---|---|
| 24-hour (or longer) app lock with uninstall protection | Planned: Device Admin plus guarding the Settings screens. Waiting for your answers |
| Emergency exit for a lock | Waiting for your answer |
| Limit by number of reels (for example 10 reels a day) | Counter exists now; the limit by count is not built |
| Remote rule updates (fix Instagram changes without a new app) | Not built |
| Block Instagram Explore | No verified ID |
| Greyscale mode | Not built |
| Interval budgets (10 minutes every 2 hours) | Not built |
| Unlock with an NFC tag | Not built |
| Earn minutes with exercise | Not built |
| Garden or streak picture | Not built |
| Accountability buddy / backend | Not built |
| Minimal launcher mode | Not built |
| Chrome extension, iOS app | Not built |
| Signed release APK for sharing, final app id | Not built |
| Time-on-screen counted for other apps (all apps, not just the social ones) | Not built |
