# Glume: Immich for Android TV

A remote-first Android TV / Google TV client for a self-hosted [Immich](https://immich.app) library, built with Jetpack Compose for TV. It currently runs on a **demo library** (no server needed) that mirrors the design prototype.

## Build and run

Requirements: Android Studio's bundled JDK (or JDK 17+) and Android SDK 37.

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug          # build
./gradlew :app:installDebug           # install on every connected device
./gradlew :app:testDebugUnitTest      # unit tests
```

### Android TV emulator (1080p)

```sh
~/Library/Android/sdk/emulator/emulator -avd Glume_TV_1080p &
./gradlew :app:installDebug
adb shell am start -n com.android.glume/.MainActivity
```

### Google TV Streamer over the network

1. On the Streamer: Settings › System › About, then click *Android TV OS build* 7 times to enable developer options. Then turn on Settings › System › Developer options › *Wireless debugging* (or *Network debugging*).
2. Pair once with `adb pair <ip>:<pairing-port>` and the code shown on screen, then `adb connect <ip>:<port>`.
3. `./gradlew :app:installDebug`. The app appears in the TV's app row as **Glume**.

### Driving it from the computer

`adb shell input keyevent` with `19 20 21 22` for the D-pad, `23` for OK (`--longpress 23` to hold), `4` for Back and `85` for Play/Pause.

## Demo walkthrough

- **Connect:** scan the QR with your phone and sign in there, or use the on-TV form. Debug builds prefill the public demo (`https://demo.immich.app`, `demo@immich.app` / `demo`). The TV stores only a limited API key; revoke it any time in Immich › Account Settings › API Keys.
  - Emulator tip: `adb forward tcp:8942 tcp:8942`, then open the link logged under `adb logcat -s GlumePairing` with `localhost:8942`.
- **Profiles:** the accounts signed in on this TV; *Add user* pairs another account. Photos are still demo data until the real library layer lands.
- **Home:** ◀ at column 0 opens the rail. Back goes from content to the rail to Home.
- **Hold OK** on a photo, album, person or memory to open the context menu. **⏯** starts a slideshow from the focused item.
- **Viewer:** ◀ ▶ browse, ▲ ▼ zoom, OK shows or hides the info overlay. Videos play a public test clip.
- **Settings › Accessibility:** high contrast, larger text and reduce motion.
- **Settings › Server › Simulate server unreachable:** shows the offline banner.
- **Debug overlay:** ▲ ▲ ▼ ▼ ◀ ▶ ◀ ▶.

## Layout

```
app/src/main/java/com/android/glume/
  data/            ImmichRepository (interface), SettingsStore, mock/ (demo library)
  domain/          immutable UI models
  ui/theme/        design tokens (colors, type, motion, dims)
  ui/components/   FocusSurface, FocusMemory, tiles, controls, images (ThumbHash)
  ui/app/          shell, rail, back handling, modals (context menu, keyboard), debug overlay
  ui/screens/      connect, profiles, home, timeline, albums, people, places,
                   collection (album/person/place/favorites), search, settings, viewer, slideshow
```

Decisions and design gaps are in [docs/DECISIONS.md](docs/DECISIONS.md).

## Licenses

Manrope (SIL OFL, `assets/licenses/OFL-Manrope.txt`). ThumbHash algorithm by Evan Wallace (MIT). The demo photos and avatars are the design handoff's sample art.
