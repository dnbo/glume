# Project: Immich TV (Android TV / Google TV client for Immich)

## What we're building
A fast, polished, remote-controlled (D-pad) Android TV app for browsing a self-hosted Immich photo library: timeline, albums, people, places, search, memories, a fullscreen viewer, and a slideshow/screensaver mode. It's a personal project for my own Immich server, so optimize for feel and speed over feature breadth.

Primary test device: Google TV Streamer (real hardware, via adb over network). Also verify on the Android TV emulator at 1080p.

## Design source of truth
The visual design comes from the Claude Design handoff bundle I'm passing alongside this prompt. Treat it as the spec for layout, spacing, colors, typography, focus states and motion. Do not invent visuals that contradict it. If something is ambiguous or missing, make the simplest TV-appropriate choice and list it in a "Design gaps" section in docs/DECISIONS.md. Start by mapping the design tokens (colors, type scale, spacing, radii, focus styles, motion durations) into a Compose theme before building any screens.

## Tech stack (decided, don't re-litigate)
- Kotlin, Jetpack Compose for TV (`androidx.tv:tv-material`, `tv-foundation`), Compose BOM
- Media3 / ExoPlayer for video (hardware decode)
- Coil 3 for image loading, with explicit memory + disk cache sizing
- Ktor client + kotlinx.serialization for the API layer, OR a client generated from Immich's OpenAPI spec (your call, explain the choice briefly). Immich's API changes between versions, so fetch the OpenAPI spec from my running server and generate or verify against it. Do not rely on endpoint names from memory.
- Koin or Hilt for DI (pick one)
- Paging 3 or a custom windowed loader for the timeline
- DataStore for settings; store the server URL and credentials encrypted with an Android Keystore-backed approach
- Single-activity, Compose Navigation, unidirectional state (ViewModel + StateFlow)
- minSdk 28, targetSdk current. The project was created as "No Activity", so add MainActivity, the manifest TV entries (leanback feature, touchscreen not required, LEANBACK_LAUNCHER category, 320x180 banner) and the TV theme yourself.
- Do NOT use Rust, WebView, or any web UI. Do NOT copy code from the Immich mobile app (AGPL); studying its approach is fine.

## Performance is the top requirement
TV apps must feel instant. Treat these as hard rules:
1. Never load original images in grids or the viewer by default. Use Immich's server-generated thumbnails for grids and the preview-size image for the viewer. Original only on explicit request (settings: "Original quality").
2. Show the asset's ThumbHash (or equivalent placeholder from the API) immediately, then crossfade to the real image.
3. Virtualized grids (LazyVerticalGrid / TvLazyVerticalGrid equivalents) with stable item keys. Tiles must be cheap composables: no nested layouts, no per-tile heavy effects.
4. Request images at the display size, not larger. Configure Coil with a bounded memory cache (~20-25% of available app memory) and a disk cache (~500MB-1GB, configurable).
5. Prefetch the next 1-2 rows in the direction of travel. In the viewer, prefetch previews for the next and previous 2 assets.
6. Focus animations only use scale and alpha (graphicsLayer). Avoid animated blur, large shadows, and layout-changing animations in lists. The ambient blurred backdrop must be cheap (pre-scaled tiny bitmap, or a static tinted gradient) and degrade gracefully.
7. Timeline uses Immich's time-bucket endpoints so the scrubber can jump anywhere without loading everything. Load buckets lazily.
8. Keep work off the main thread. Avoid recomposition storms: stable/immutable UI models, `remember` where needed, check with Layout Inspector and recomposition counts.
9. Cold start to first visible content under ~1.5s on the Streamer: cache the last Home rows locally and render them instantly while refreshing in the background.
10. Add a debug overlay (toggle via a hidden key combo) showing FPS/jank and cache hit rates, so I can judge feel on the TV.

## Features and phases
Build in phases. Finish and commit each phase, and tell me how to test it on the device, before starting the next. Stop and summarize after each phase.

Phase 0, Foundation
- Project setup, TV manifest, theme from design tokens, navigation shell with the left nav rail (collapsing icons + labels), focus management basics, an app-wide focus-ring component.
- docs/DECISIONS.md and a README with build/run/adb instructions.

Phase 1, Connect and Home
- Server URL entry + API key auth (simplest path first). Validate against the server, handle unreachable/invalid-key errors with retry. Persist securely.
- Profile/user handling if the design includes it (single user via API key is fine for v1; note multi-user as future).
- Home screen per design: memories/"On this day" hero, rows for Recently added, Favorites, Recent albums, People, and a Continue/Resume row.
- Skeleton loaders, empty states, offline/unreachable state.

Phase 2, Albums and Viewer
- Albums grid and album detail (cover, title, count, shared indicator, contributors).
- Fullscreen photo viewer: left/right navigation, OK toggles info overlay (date, location, album, camera, people), overlay auto-hides after ~4s, zoom with up/down if designed, Back exits cleanly to the exact previous focus position.
- Video playback with Media3: play/pause, +/-10s seek, TV-style scrubber, remembers position, hardware decode. Show duration badges on tiles.
- Long-press OK context menu (favorite/unfavorite, add to album, play slideshow from here).

Phase 3, Timeline
- Grouped-by-month/day grid with sticky date headers, fast-scroll scrubber on the right edge navigable with the D-pad (hold up/down to jump by month/year), videos badged. Focus restoration after returning from the viewer is critical, and it must be perfect.

Phase 4, Slideshow and Screensaver
- Fullscreen slideshow with Ken Burns (cheap, GPU-friendly), crossfades, configurable interval/transition/shuffle/captions/include-videos, and sources (all, album, person, favorites, memories).
- Android TV screensaver via a DreamService ("Daydream") so it can be selected as the system screensaver, plus an in-app idle trigger.
- Low-power friendly: avoid decoding oversized images; prefetch one ahead.

Phase 5, People, Places, Search, Memories
- People grid + person detail (+ slideshow of a person), Places grid, Memories playback.
- Search: on-screen-keyboard-friendly UI per design, filter chips, smart/natural-language search if the server exposes it, results grid, empty state. Voice search via the system recognizer if feasible.

Phase 6, Settings and polish
- Server info/storage, image quality, cache clearing (and size display), slideshow defaults, screensaver timeout, about/version, text-size/high-contrast options if designed.
- Accessibility: content descriptions, sensible focus order, minimum text sizes from the design.
- Error handling, retries with backoff, graceful degradation on slow connections (low-res first, then sharp).

## D-pad and focus rules
- Every interactive element is reachable with D-pad only and has a clear focus state from the design. No touch assumptions, no hover.
- Back always goes up exactly one level and restores the previous focus target (remember focused item keys per screen).
- Use TV-specific focus APIs deliberately (FocusRequester, focusRestorer, focusProperties); avoid focus traps and unreachable elements. Test edge cases: first/last item, empty rows, rows with different lengths, return from viewer.
- Respect TV safe areas (~5% margins).

## Read-only client (hard rule)
The app must never be able to remove or alter photos on my server.
- Allowed writes, and only these: favorite/unfavorite an asset, and add an existing asset to an existing album.
- Never call delete, trash, restore, archive, hide, edit/metadata-update, album-create/delete/rename, remove-from-album, upload, or any admin endpoint. Don't add such methods to `ImmichRepository`, even unused.
- Sign-in exception: during sign-in only, the password session may create the TV's limited API key and revoke this TV's own previous key for the same account, then must log out. Passwords and session tokens are never stored.
- Note: Immich has no favorite-only permission; favorites need `asset.update`, which the server would also accept for metadata edits/archiving. The app's code is the guard for those.
- The API key should carry only read permissions plus the two writes above (exact permission names: verify against my server's OpenAPI spec). If the server rejects a write for lack of permission, show a toast; never ask for broader permissions.
- UI actions that would need a forbidden write (e.g. "New album…", "Hide this memory") stay as a toast pointing to the Immich app, or are removed.

## Architecture guidance
- Layers: `data` (api, repository, cache), `domain` (models, use cases only if they earn their place), `ui` (screens, components, theme, navigation).
- Keep the API layer isolated behind an `ImmichRepository` interface so the UI never touches HTTP types. Map API DTOs to small immutable UI models.
- Cache: Room (or a simple file/DataStore cache) only for what speeds up cold start (Home rows, album list, timeline bucket index). Don't over-engineer.
- No premature abstraction. Prefer plain, readable code that I can maintain; I'm an experienced Angular/TypeScript and full-stack developer, so explain Android-specific idioms briefly where they differ from what I'd expect.

## Working agreement
- Start in plan mode: read the design handoff, inspect the repo, then give me a short plan for Phase 0 and 1 with any questions (max 5). Then proceed.
- Make small, reviewable commits with clear messages, one logical change each.
- Add unit tests for the repository/mapping layers and the focus-restoration logic where practical, plus a few Compose UI tests for the nav rail and grid focus. Don't chase coverage numbers.
- After each phase: build, run on the emulator, list what's done, what's deferred, what I should verify on the Streamer, and any performance observations.
- When unsure about an Immich API detail, check the OpenAPI spec from my server (I'll give you the URL and an API key via environment variables or a local, git-ignored `local.properties` entry). Never commit secrets.
- Never block on perfection: if a design detail is costly or risky for performance, implement the cheap version and flag it.

## Out of scope for now
Uploading/backup from the TV, editing photos, admin features, multi-server, Tizen/other platforms, Rust core. Keep the architecture clean enough that a Rust core or other targets could be added later without rewriting the UI.