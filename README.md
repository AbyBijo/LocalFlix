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
