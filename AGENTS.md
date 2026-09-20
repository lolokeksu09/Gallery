# Gallery development rules

- This repository is ONLY the Android Gallery, never RiftCore.
- Read docs/SPEC.md, docs/ARCHITECTURE.md and docs/PROGRESS.md before changes.
- Kotlin + Compose, Android 13+, true black AMOLED background.
- No internet permission, networking libraries, telemetry, accounts, advertising, or background services.
- Request photo and video access separately. Video is opt-in. Respect Android 14 selected-media access.
- Never delete media without the platform confirmation. Never modify originals for previews.
- Keep dependency count and modules small. No unrelated refactors.
- Test and lint before claiming completion; successful compilation is not physical-device validation.
- Update PROGRESS with the exact verified status and next action in every meaningful implementation commit.
- Never put release signing keys in source. Debug APKs are test builds, not stable releases.
