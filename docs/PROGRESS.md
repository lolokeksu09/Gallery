# Progress

## 0.1.7 media type filter (pending CI)
- Only media types a phone produces are read from MediaStore. A downloaded web project had put 127
  AVIF sprites into an album, where they did not even render. Filtering happens in the query, so
  such files never enter the application.
- The filter is an allowlist and therefore hides silently when an entry is missing; MediaTypesTest
  guards the formats a camera, screenshot or messenger produces.
- Not verified: not run on a device. The user should confirm the album of web assets is gone and
  that nothing real went with it.

## 0.1.6 privacy, trash, thumbnails and multi-select (built green, audited, not device-tested)
- FLAG_SECURE while the vault is open or unlocked; the unlocked vault no longer reaches the recents
  snapshot. Wrong-password count persisted, so a force stop no longer bypasses the lockout.
- Gallery deletions go to the system trash; the vault import still really deletes the original,
  because a trashed original would stay visible in the system trash.
- Stale favorite keys pruned on refresh, except under limited access.
- Video tiles read MediaStore thumbnails instead of decoding frames. Coil's disk cache was checked
  first and rejected: it only serves network sources, so it would have been a no-op here.
- Multi-select with batch share, favorite, hide and delete. One system dialog per batch.
- Verified: build 35532400821 green on 03b1367. APK 11,390,034 bytes, SHA256
  94da1205999462e11d5818f70fac88b013967c1f82b2a81c174e1d2f0943cb0f. That is 1,131,257 bytes more
  than 0.1.3; the growth is the new code, not a packaging regression.
- A second audit of the batch work found six problems, all fixed and rebuilt green. The worst was
  introduced by this release: pruning favorites was guarded only against limited access, but with
  the permission revoked the library reads empty and every favorite would have been deleted.
  Pruning is gone; the count in settings is taken against the library instead.
- Not verified: nothing in 0.1.6 has run on a device.

## Next
1. Device test: batch hide with the dialog refused (nothing may be lost), batch delete and restore
   from the system trash, rotation during an active selection, video scroll speed.
2. Physical Android 13 smoke tests still outstanding from earlier releases.
3. Favorites still key on the content URI; a stable key needs a migration.

## 0.1.5 colour system and silent vault (pending CI)
- Hiding a file says nothing at all: a success or refusal message naming the file or the vault would
  reveal the vault to anyone watching the screen. Only an unlabelled spinner shows during encryption.
- New palette system in Theme.kt: accent, backdrop gradient, chrome, card, border and muted colours move
  together, read through LocalGalleryPalette instead of being hard-coded per screen. Five themes chosen
  in settings and applied immediately: Аметист (default), Закат, Океан, Мята (the previous colours) and
  Чернила. Surfaces are deep but tinted; pure black remains behind media and for scrims.
- User confirmed on device that 0.1.4 works: five taps, password, move with the system dialog, restore
  and playback all behaved. That covers the vault flow that CI cannot reach.
- Not verified: the new palettes and the silent move have not been seen on a device.

## 0.1.4 private vault (built green, audited, not device-tested)
- Vault reachable only by five taps on the already open Settings tab; the "move to vault" action shows in
  the viewer only while the vault is unlocked. No other entry point exists.
- AES-GCM under a random data key wrapped by a PBKDF2 key from the password. Media is stored as
  independent 1 MiB frames binding their index as associated data, with a terminator frame, so
  reordering, splicing and truncation fail to decrypt and memory stays bounded.
- Import encrypts, then verifies by decrypting the copy and comparing SHA-256 AND the byte count against
  the size MediaStore reports, then asks for the platform delete confirmation. A refused confirmation
  discards the vault copy.
- Verified: build 35520968624 green on 0e18847 (compilation, VaultCryptoTest, lint, permission audit).
  The frame format was additionally exercised against a Java mirror locally, 31 checks.
- Audit findings fixed on top of that build:
  - Import verification was self-referential: a source ending early hashed consistently with the short
    copy, so verification passed and the original was then deleted. The byte count is now compared with
    the size MediaStore reports, and an empty read is refused.
  - ON_STOP also fires on rotation, so turning the phone locked the vault and dropped the user back to
    the password gate. Locking now skips configuration changes.
  - Progress and failures during hiding were drawn inside the vault screen, which is closed during that
    flow, so encryption progress was invisible and errors were swallowed. Both moved to the caller.
  - A second tap on "move to vault" started a second import and orphaned the first encrypted copy.
  - materialize() reused any non-empty cache file, so a kill mid-decrypt served a truncated file forever,
    and decrypted copies survived the process. Decryption now goes through a part file, only files this
    process completed are reused, and startup clears the cache.
  - changePassword had no try/catch and would crash the application on a storage failure.
  - Cache and vault-file deletion ran on the main thread from the lifecycle observer.
  - Unreadable vault metadata was dropped silently, hiding files permanently; the count is now reported.
- Not verified: nothing ran on a device or emulator. Five taps, password entry, a real move with the
  system delete dialog, encrypted video playback and restore to the gallery need the phone.

## Next
1. Device test of the vault, then of the 0.1.3 motion and grid work that is also still unverified.
2. Physical Android 13 smoke tests: denied/granted permission, photos only, optional video, empty/large
   library, zoom/swipe, rotate, sharing, deletion confirm/cancel, permission revocation, airplane mode.
3. Add metadata paging if large-library measurements justify it.

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
- Build verified: https://github.com/lolokeksu09/Gallery/actions/runs/35518294962 (commit 393baf5).
  Unit tests, Android Lint, assembleDebug and the APK permission audit passed.
- Measured effect of the build work, so it is not overstated:
  - APK 10,258,777 bytes against 10,281,374 for 0.1.1: 22,597 bytes, 0.22%. R8 was already removing the
    unused material-icons-extended classes, so dropping the dependency, the ru/en resource limit and the
    metadata exclusions changed almost nothing in the package. The reduction does absorb the code added
    for this release, but it is not a size win worth claiming.
  - Build step 3m39s and 3m22s against 3m33s for 0.1.2: no measurable change. gradle/actions/setup-gradle
    runs the cache read-only on non-default branches ("Cache is read-only: will not save state"), so the
    Gradle configuration cache and build cache cannot pay off here. Any gain would first appear on main.
  - The changes are kept because they are correct and cost nothing, not because they were shown to help.
  - CI now prints APK size and SHA256, so the next comparison needs no artifact download.

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
