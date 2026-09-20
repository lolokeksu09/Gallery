# Gallery development rules

- This repository is ONLY the Android Gallery, never RiftCore.
- Read docs/SPEC.md, docs/ARCHITECTURE.md and docs/PROGRESS.md before changes.
- Kotlin + Compose, Android 13+, AMOLED-dark surfaces. Bars and cards stay near black; the media grid
  backdrop carries a faint colour tint so photo gaps are not a flat black sheet.
- Motion matters to the user: tab switches, viewer open/close, zoom and grid density must animate.
  Never introduce an abrupt state jump where a transition is possible.
- No internet permission, networking libraries, telemetry, accounts, advertising, or background services.
- Request photo and video access together on user action. Both are enabled by default. Respect denial, partial grants and Android 14 selected-media access; no permission may be silently granted.
- Never delete media without the platform confirmation. Never modify originals for previews.
- Keep dependency count and modules small. No unrelated refactors.
- Test and lint before claiming completion; successful compilation is not physical-device validation.
- Update PROGRESS with the exact verified status and next action in every meaningful implementation commit.
- Never put release signing keys in source. Debug APKs are test builds, not stable releases.
