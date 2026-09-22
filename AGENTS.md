# Gallery development rules

- This repository is ONLY the Android Gallery, never RiftCore.
- Read docs/SPEC.md, docs/ARCHITECTURE.md and docs/PROGRESS.md before changes.
- Kotlin + Compose, Android 13+, Material 3 on an AMOLED panel. The page is true black; everything
  that is not media sits on the tonal container ramp above it. A theme in Theme.kt defines accent,
  card, chrome, elevated, border and muted together, and every screen reads them from
  LocalGalleryPalette rather than hard-coding colours. The user picks the theme in settings.
- No paragraph explaining a control, no file count in a screen title, no statistics card: a setting
  that needs a paragraph is the wrong control. Russian counts the noun in three forms, so counts go
  through fileCount(), never "$n файлов".
- Motion matters to the user: tab switches, viewer open/close, zoom and grid density must animate.
  Never introduce an abrupt state jump where a transition is possible.
- No internet permission, networking libraries, telemetry, accounts, advertising, or background services.
- Request photo and video access together on user action. Both are enabled by default. Respect denial, partial grants and Android 14 selected-media access; no permission may be silently granted.
- Never delete media without the platform confirmation. Never modify originals for previews.
- Keep dependency count and modules small. No unrelated refactors.
- Run ./tools/verify.sh before every push. It needs no Android SDK and catches resource, drawable and
  icon mistakes in seconds; the sandbox cannot compile, so CI is the only compiler.
- Test and lint before claiming completion; successful compilation is not physical-device validation.
- Update PROGRESS with the exact verified status and next action in every meaningful implementation commit.
- Every version bump adds an entry to docs/CHANGELOG.md: what changed since the previous version, plus the
  CI build link, APK size and SHA256. Say when a number was not measured instead of guessing it.
- Commit each change on its own rather than batching unrelated work into one commit.
- Never put release signing keys in source. Debug APKs are test builds, not stable releases.
