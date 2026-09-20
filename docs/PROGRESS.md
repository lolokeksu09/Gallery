# Progress

## 0.1.3 motion, tinted grid, build optimization and bug fixes (pending CI)
- Photo and album grids sit on a faint green-tinted backdrop instead of pure black, so the gaps between
  photos read as colour. Bars, cards and thumbnails stay near black for AMOLED.
- Motion pass: tab/album switches use AnimatedContent; the viewer fades and scales in and out over the
  home screen instead of replacing it; double-tap zoom eases over 260ms; viewer bars slide and fade;
  grid density changes animate item placement, gaps and corners; Coil crossfades thumbnails; settings
  selections animate their colours.
- Build: removed androidx.compose.material-icons-extended (four in-repo vector drawables replace the
  five icons it supplied), disabled unused build features, excluded packaging metadata, dropped APK
  dependency metadata, limited resources to ru/en, enabled the Gradle configuration cache and in-process
  Kotlin compilation.
- Bug fixes:
  - Android 14 grants READ_MEDIA_VISUAL_USER_SELECTED alongside full access, so the app wrongly reported
    limited access and showed the "select more" button with full access. partial() now excludes that case.
  - refresh() cleared media before reloading, so every resume and every MediaStore change blanked the grid
    and flashed a spinner. Old media now stays until the new list arrives.
  - Cancelling the system delete dialog closed the viewer. It now stays open unless the deletion succeeded.
  - formatDuration showed "90:00" for a 90-minute video; hours are now rendered as h:mm:ss.
  - GalleryViewModel.onCleared did not call super.
  - MediaRepository.read carried a dead includeVideos parameter that was always true.
- Audit fixes on top of the above:
  - Vector drawables used ?attr/colorControlNormal, an AppCompat attribute this project does not have;
    aapt2 failed the build. The XML tint is gone (Compose Icon tints through a ColorFilter anyway).
  - The open viewer held the media snapshot taken when it opened, so refreshes never reached it. It now
    follows the live list while open and only freezes for the exit animation.
  - Reopening the viewer within the 180ms exit window reused the old pager state and ignored the tapped
    photo. Each open now gets its own session key.
  - A fast pinch could lose a step because the next column count was read from the value still making its
    round trip through DataStore. The gesture now drives a local count that the stored value syncs into.
  - The filtered and sorted list was memoised three times with the same keys; it is built once and passed
    down, and favorites no longer invalidate it outside the favorites tab.
- versionCode 4 / versionName 0.1.3.

## 0.1.2 photo, video and settings update (build-verified in CI)
- Photo grid: gaps between tiles (5-10dp, scaled with column count), rounded corners, date headers with a file count. Tiles no longer touch each other.
- Pinch with two fingers on the photo grid changes the column count 2-5; the gesture is read on the initial pointer pass and consumed only while two fingers scale, so single-finger scrolling is untouched. A short "N в ряд" badge confirms the change. The value is the existing density setting, so grid and settings stay in sync.
- Viewer: Scaffold replaced with an overlay layout. Video starts playing immediately and, after the Media3 controller times out (5s), application bars and system bars hide together, so video fills the screen with no application borders. Tap or back restores the chrome; system bars are restored when the viewer closes.
- Settings tab redesigned: library overview card (photos/videos/favorites), grid density picker with miniature layout previews, sorting choices, access status pill and access action rows. No version label or marketing text, per 0.1.1 feedback.
- versionCode 3 / versionName 0.1.2. Added explicit androidx.core:core-ktx for WindowInsetsControllerCompat.
- CI now also builds pushes on claude/** branches; dl.google.com is blocked in the development sandbox, so the Android SDK cannot be installed there and compilation must be verified by GitHub Actions.
- Build verified: commit 62ebd2c6dfc4ce90db1c1eceee32809c2f4526ac, https://github.com/lolokeksu09/Gallery/actions/runs/35516392490 - unit tests, Android Lint, assembleDebug and the APK permission audit all passed; APK artifact uploaded (not downloaded here, so no size/SHA256 recorded for this run).
- Not verified: nothing was run on a device or emulator. Grid gaps, the pinch gesture, full-screen video timing and the new settings layout are unconfirmed visually and need user testing on the phone.
- User statement (not verified here): their phone runs an Android patch that skips signature verification, so a changing debug certificate does not block updates. Persistent release signing is therefore no longer treated as a blocker for delivering test builds to this user.

## 0.1.1 verified build
- Commit: 00483ecd30e74efed7552e768757bbeddb9b3042. Successful build/tests/lint/permission audit: https://github.com/lolokeksu09/Gallery/actions/runs/35515287324
- APK: 10,281,374 bytes. SHA256: 85e4f3f973919266a1b371ee97486427b03313f4a8cee248217e87562c275c6e.
- Certificate differs from 0.1.0: in-place update is NOT possible. Reinstallation loses Gallery favorites/settings but not shared photos/videos. Warn user; do not delete anything automatically. Establish persistent signing before next distribution.
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
