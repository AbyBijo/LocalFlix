# LocalFlix

A local video library and player for Android — VLC-level utility, Netflix-level feel, in stages.

## Status: Iteration 1 — Gradle baseline

This iteration converts the original AIDE pure-Java project to Gradle and
Android 16 (SDK 36), renames the package to `com.media.localflix`, and adds
a GitHub Actions debug build. It keeps the original MediaPlayer player and
programmatic Java UI, so every step still installs and works.

Changes vs the AIDE original, and nothing else:

- Gradle project (AGP 8.10.1, Gradle 8.11.1, JDK 17), `compileSdk` / `targetSdk` 36, `minSdk` 23
- Package `com.localflix.app` → `com.media.localflix` (fresh install: old
  favourites and resume positions do not carry over)
- Launcher activity now declares `android:exported="true"`, required from API 31
- Media permission modernised: `READ_MEDIA_VIDEO` on Android 13+, the old
  `READ_EXTERNAL_STORAGE` only up to API 32 — without this the library
  would stay empty on current Android

## Build

CI builds a debug APK on every push to `main` (Actions → Artifacts →
`localflix-debug-apk`). Locally, with an Android SDK installed:

```sh
gradle :app:assembleDebug
```

No signing keystore is configured yet — iteration 1 is debug-only on purpose.
A signed release build comes with a later iteration, after the user confirms it.

## Staged plan (agreed)

1. Gradle + SDK 36 + CI debug APK (this iteration)
2. Netflix-style library: Continue Watching, detail sheet, better thumbnails, filters
3. VLC-style player: subtitles, picture-in-picture, background play,
   playlists, sleep timer, per-video settings memory
4. Polish: edge-to-edge, tablets / large screens, performance, accessibility,
   16 KB page-size readiness

Media3 / ExoPlayer is planned for a later stage, not iteration 1: the staged
plan keeps the working MediaPlayer first, then moves to Media3.

## Iteration 2 — Standout: it knows your shows (prepared, not yet pushed)

Most local players treat every file as an isolated video. LocalFlix's
standout is series awareness, entirely offline:

- `SeriesParser` reads filenames like `Show S01E02 - Title`, `Show 1x02`,
  `Show Season 1 Episode 2` and groups them into series / season / episode.
  A bare trailing number is never treated as an episode, so film sequels
  are not mis-grouped.
- `ContinueWatching` shows a Home-tab row of in-progress videos, most
  recently watched first, with progress, time remaining, and — for series —
  the next episode (`Next: S1 E3`). Finished videos never appear.
- The player saves a last-seen time alongside every resume position, so
  the row orders correctly even after a restart.

This is pushed as a separate step after the iteration 1 CI build passes,
so a failure in either step points to exactly one change.

## Iteration 3 — Life, weight, and cutout-safe (v1.1.0)

Every element was audited, not just the player:

- **Camera curve / display cutout (the reported bug):** the window is now
  `LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER`, so it never extends under a
  punch-hole camera. On top of that, the library and the player each
  apply the real system + cutout insets: the library pads in from the
  status bar, side curves and gesture bar in any rotation, and the
  player top bar drops below the camera while the bottom bar rises
  above the gesture bar, with side padding in landscape.
- **Library:** gradient background, cards with a hairline edge and soft
  elevation, larger titles, series labels in card meta (`S1 E2`),
  press feedback on cards, folders, nav, sort and scan, scan spins,
  sort pops, tab changes fade, search hint covers series and folders.
- **Continue Watching:** thicker progress, elevation, press feedback,
  haptic on tap, staggered slide-fade entrance.
- **Thumbnails:** 720px wide (was 480px) and fade in instead of popping.
- **Player:** controls fade in and out, HUD pops, speed/aspect/audio
  panels fade in, every button gets press feedback and haptic,
  favourite heart pops in the library and in the player.
- **Weight:** the app is deliberately heavier to look at and to touch —
  elevation, gradients, 720px thumbs, motion and haptics — instead of
  feeling like a thin 52 KB utility. Size alone was never the goal;
  the feel was.
