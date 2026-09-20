# Progress

## 0.1.1 changes (awaiting CI)
- Compact adaptive album cards; removed technical settings text and offline header suffix.
- Photo and video library enabled together by default. Both permissions requested together on user action; old video-off preference no longer hides video.
- Optimized CI: Gradle task cache, two workers, SDK reuse, cancellation of superseded builds, documentation-only push filtering. R8 and all checks remain enabled.
- User supplied screenshots confirm 0.1.0 launches and renders albums/settings on their device. Other runtime checks remain unverified.

## Implemented and build-verified
- Separate Android source project; no RiftCore files.
- AMOLED UI, photo grid/day groups, albums, favorites, sorting, density settings.
- MediaStore access, optional videos, Android14 limited selection.
- Viewer, zoom, Media3 playback, share, details, system-confirmed delete.
- Offline manifest and APK permission audit in GitHub Actions.
- Duration unit tests.

## APK 0.1.0 ready for device testing
- Built commit: 6c91585a5ed7c4359bdf56f45ed83232ff33e5ec.
- Successful run: https://github.com/lolokeksu09/Gallery/actions/runs/35514670112
- Unit tests, Android Lint, assembleDebug and actual APK permission audit passed.
- R8/resource shrinking reduced APK from 67,084,094 to 10,330,530 bytes.
- Downloaded and saved as Gallery-0.1.0.apk for delivery in chat.
- SHA256: c2c1d149325d80424a3424df1b3bf2a9d514dd0191b02dca43b2f42d6cbb40ae
- APK permissions: READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_VISUAL_USER_SELECTED, app-local signature-level DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION. No network permission.

## Next
1. Physical Android13 smoke tests: denied/granted permission; photos only; optional video; empty/large library; zoom/swipe; rotate; sharing; deletion confirm/cancel; permission revocation; airplane mode.
2. Establish stable private signing before stable updates. Current CI debug signing does not guarantee cross-run update compatibility. Uninstalling removes app-local favorites/settings; warn before recommending it.
3. Add metadata paging if large-library measurements justify it.

No emulator or physical-device execution performed. Successful compilation is not complete runtime validation.

## Resolved
- Replaced obsolete SDK package tools with platform-tools.
- Added missing enableEdgeToEdge import and Media3 UI API opt-in.
- Unzoomed drags yield to pager using conditional transformable panning.
