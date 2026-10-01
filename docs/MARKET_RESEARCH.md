# Market research: short-form video blockers on Android (Oct 2026)

## The problem

Short-form feeds (YouTube Shorts, Instagram Reels, Facebook Reels, Snapchat Spotlight, TikTok)
are built for endless consumption: no natural stopping point, autoplay, a variable-reward feed.
Most people don't want to delete YouTube or Instagram. They need them for messages, tutorials
and subscriptions. They want to lose the addictive part only.

## What exists

| Category | Examples | How they work | Weak spots |
|---|---|---|---|
| Whole-app blockers / timers | Digital Wellbeing, AppBlock, Opal, Freedom, Stay Focused | Block or time-limit the whole app | Too blunt: blocking Instagram also blocks DMs, so people turn the blocker off |
| Friction apps | one sec, ScreenZen, ClearSpace | Show a breathing or wait screen before an app opens | Easy to tap through, and once you're in, the feed is endless again |
| Short-form blockers | ScrollGuard, Shortstop, NoScroll, Reels Blocker, Shorts & Reels Blocker, Basta!, plus open-source Scrolless, FocusBlock, ShortFormBlocker, AntiScroll | Accessibility service finds the Shorts/Reels screen and presses Back | False positives (see below), easy to switch off, many are ad- or subscription-funded and ask for internet access |
| Modded clients | ReVanced, Instander | Patched apps with Shorts removed | Against app ToS, fragile, needs patching on every update |
| Browser extensions | Unhook, DF Tube | Hide Shorts on the web | Only work in a desktop browser |

### How short-form blockers detect content

Every serious Android blocker uses the **AccessibilityService API**:
1. Listen for window-state, content-change and scroll events from the target apps.
2. Inspect the view tree for resource ids that belong to the short-video player
   (YouTube: `reel_player_page_container`, `reel_recycler`, `reel_watch_player`; Instagram:
   `clips_viewer_view_pager`, `clips_viewer_root`; Snapchat: `spotlight_*`), for the
   **selected bottom-nav tab** (`Shorts`, `Reels`, `Spotlight`), or, for Facebook, which strips
   resource ids, for player labels such as `Reel details`.
3. Call `performGlobalAction(GLOBAL_ACTION_BACK)`.
4. Browsers: read the address-bar view (`url_bar`, `mozac_browser_toolbar_url_view`, …) and
   match `youtube.com/shorts`, `instagram.com/reels`, `tiktok.com`.

### Common complaints about existing apps (from reviews and issue trackers)

1. **False positives.** The Shorts shelf on the YouTube home feed and inline reels on the
   Instagram home feed reuse the same view ids as the player. Naive blockers bounce you out of
   the normal feed and can trap you in a Back loop. One open-source project removed
   `reel_recycler` and `clips_video_container` for this reason.
2. **Hidden pager pages.** Instagram keeps the Reels page attached behind the home feed, so
   matching ids without checking visibility blocks everything.
3. **Too easy to disable.** Turning a blocker off when the urge hits takes one tap.
4. **All or nothing.** Some people want "10 minutes of Shorts a day", not zero.
5. **Endless feeds aren't only short videos.** The Instagram home feed, X, Reddit and LinkedIn
   are just as bottomless.
6. **Privacy.** An app that can read your screen *and* reach the internet is a big trust ask.
7. **Sideloading friction.** Android 13+ greys out the Accessibility toggle for sideloaded apps
   ("Restricted setting") and users don't know the workaround.

## How ReelOff answers each one

| Gap | ReelOff |
|---|---|
| False positives | A player view id counts only if it is **visible** *and* covers **≥70% of the screen**. Shelves and 4:5 feed posts (~55%) never qualify. Covered by unit tests. |
| Back loops | Back first. If Back fails twice in 4 s, ReelOff goes Home. Actions are debounced. |
| Easy to disable | **Commitment timer**: pausing, turning a blocker off, raising an allowance or swipe budget, or shortening the wait itself means sitting through a countdown (10 s to 5 min) with mindful nudges. Making things stricter is instant. A pause always ends on its own. |
| All or nothing | Optional **daily allowance** per platform (5/10/15/30 min). Time away isn't counted, the allowance resets at midnight and survives restarts. |
| Endless feeds | **Endless-scroll limiter** for Instagram, Facebook, X, Reddit, Threads, LinkedIn, Pinterest, YouTube, Snapchat and ShareChat: a swipe budget per sitting, then the feed closes for a break you choose. |
| Privacy | **No INTERNET permission at all.** The service gets events only from the apps you chose (`packageNames` narrowed at runtime). No message text is read. Only the browser URL bar is read, and only in browsers. Backups are disabled. |
| Sideloading | The app explains "Allow restricted settings" and links straight to App info. |
| Regional apps | Moj, Josh, MX TakaTak and Chingari are covered as well as TikTok. |

## Play Store compliance notes

- Accessibility use that isn't for disabled users is allowed with a **prominent in-app
  disclosure** and affirmative consent before the user is sent to settings (implemented in
  `DisclosureScreen`), plus the Accessibility declaration in Play Console.
- `isAccessibilityTool="false"` is declared honestly.
- Target SDK 35.

## Sources

- https://github.com/jose99segura/ReelBlocker
- https://github.com/shubh07o/FocusBlock
- https://github.com/atick-faisal/Shorts-Blocker
- https://github.com/Lucianbai09/Reel-Blocker
- https://github.com/rosinski141/ShortFormBlocker
- https://github.com/Xeven777/Scrolless
- https://github.com/yadavnikhil03/AntiScroll
- https://play.google.com/store/apps/details?id=dev.atick.shorts
- https://play.google.com/store/apps/details?id=com.newswarajya.noswipe.reelshortblocker
- https://shortstop.app/blog/best-youtube-shorts-blocker-apps/
- https://www.getfaithlock.com/resources/screenzen-vs-one-sec
- https://habi.app/insights/best-screen-time-apps/
