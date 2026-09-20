# Progress

## Implemented, not yet build-verified
- Separate Android source project; no RiftCore files.
- AMOLED UI, photo grid/day groups, albums, favorites, sorting, density settings.
- MediaStore access, optional videos, Android14 limited selection.
- Viewer, zoom, Media3 playback, share, details, system-confirmed delete.
- Offline manifest and APK permission audit in GitHub Actions.
- Duration unit tests.

## Next
CI first attempt failed before compilation: setup-android requested obsolete SDK package `tools`. Fixed by explicitly requesting only platform-tools. Zoom now yields unzoomed horizontal drags to the pager.
1. Run GitHub build, fix compilation/lint/test failures.
2. Download successful APK artifact and deliver.
3. Physical Android13 smoke tests: denied/granted permission; photos only; optional video; empty library; large library; zoom/swipe; rotate; sharing; delete confirm/cancel; permission revocation; airplane mode.
4. Establish stable private signing before distributing updates as stable.

No APK or physical-device test is claimed by this checkpoint.
