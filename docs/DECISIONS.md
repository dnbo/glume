# Decisions

## Stack choices made in this pass

- **DI: Koin** (`di/AppModule.kt`). Less ceremony than Hilt for a single-module app, no annotation processing.
- **Navigation: Compose Navigation with type-safe routes** (`ui/nav/Routes.kt`). Asset lists for the viewer and slideshow are not serialized into routes; they are parked in `MediaQueue` and the route carries a token.
- **API layer: not built yet.** The real server wasn't reachable, so `MockImmichRepository` serves the design prototype's demo library from bundled sample photos. Everything goes through `ImmichRepository`, so a Ktor + kotlinx.serialization implementation (verified against the server's OpenAPI spec) can replace it in `AppModule` without UI changes.
- **Timeline: custom windowed loader**, not Paging 3. The bucket index (month + count) loads first, so the scrubber can jump anywhere and every month has stable, correctly sized placeholders. Each month's assets load when its header or tiles compose. This maps directly onto Immich's time-bucket endpoints.
- **Settings: DataStore Preferences.** Only the server URL and selected user are stored. No credentials exist in demo mode; the Keystore-backed secret storage lands with the real API layer.

## How focus works

- `FocusSurface` is the single focusable primitive: scale on a graphics layer plus a drawn ring and glow. OK is handled from raw key events: press on key down, **long press on the first auto-repeat** (that's what a remote sends when OK is held), click on key up.
- `FocusMemory` (one per screen, owned by its ViewModel) remembers the last focused item key. Returning from the viewer, from a detail screen, or from the rail (▶) restores exactly that item. The generic `focusRestorer` is kept only inside individual lazy rows; at the content level it fought with this and picked the wrong row.
- Rows and grids use a pivot `BringIntoViewSpec` so focus docks where the design puts it: rows keep focus in column 2, grids dock a row down, and Home scrolls itself (rows 0–1 fixed, later rows dock 150 px from the top).
- **Back** is handled in one place (`AppController.back()`), because with targetSdk 36+ predictive back delivers Back through the back dispatcher, not as a key event. The order is: modal → screen interceptors (viewer zoom, slideshow panel, settings rows, connect error) → rail logic → navigation pop.
- Modals (context menu, connect keyboard) take the D-pad at the root rather than through the focus system, so focus can't escape behind them, and the item underneath keeps focus.

## Performance notes

- 1 dp = 1 design px: `GlumeTheme` rescales density to a 1920-wide canvas, so the handoff's numbers are used verbatim.
- Grids and rows load the thumbnail URL and the viewer loads the preview URL. Coil decodes at node size. Memory cache is 22% of app memory and the disk cache is 512 MB.
- ThumbHash placeholders are decoded off the main thread into a shared LRU and drawn stretched (a cheap blur). The ambient backdrop is the focused item's ThumbHash at 32% opacity, with no runtime blur.
- Focus, Ken Burns and crossfades only animate scale, translation and alpha on graphics layers.
- The viewer prefetches previews for ±2 neighbours, and the slideshow prefetches one ahead.
- Hidden debug overlay (▲ ▲ ▼ ▼ ◀ ▶ ◀ ▶): FPS, janky frames and image-cache hit rates.

## Design gaps

Where the design was ambiguous or too costly, I made the simplest TV-appropriate choice:

1. **Focus glow and shadow.** The design's `0 0 48px` glow and deep drop shadow are blurred CSS shadows. They're drawn here as two translucent strokes around the ring, with no shadow, to keep focus moves cheap on the Streamer.
2. **Blurred backdrops** (ambient, viewer, connect/profiles). CSS `blur(60–90px)` is replaced by a stretched ThumbHash.
3. **Low-res → sharp transition** (St-Slow). Coil's 300 ms crossfade from the ThumbHash replaces the animated 16 → 0 px blur, since animated blur is explicitly ruled out.
4. **Rail open/close.** Labels appear instantly and only the background gradient animates (no width animation of the item column).
5. **QR pairing.** It's a demo-mode simulation (scan → approve → signed in). Real pairing needs a server-side flow that Immich doesn't offer yet, so the static QR is the design's placeholder.
6. **Live Photos** show the LIVE badge, but ⏯ doesn't play the motion part yet; it needs the linked video asset from the API.
7. **Video controls** use a real Media3 player streaming a public sample clip. Demo videos all play the same clip, and durations on tiles come from mock data.
8. **Unnamed faces** in the design are hue-shifted avatars; here they're plain avatars.
9. **Language and Date format** settings are stored but not applied yet (English only).
10. **Simulate slow connection** (a prototype-only toggle) was dropped. **Simulate server unreachable** is kept as a demo switch that shows the offline banner and the dimmed "Not available offline" shared albums.
11. **Screensaver.** Only the in-app idle trigger is built; the system `DreamService` is still to do (Phase 4).
12. **Home cold-start cache** (render the last Home rows instantly) isn't needed with mock data. It's deferred to the real repository.
