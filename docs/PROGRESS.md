# Progress

## 0.1.12 tile transform, predictive back, date while scrolling (confirmed on the device)
- The user confirmed every outstanding device check and asked for a more modern interface. Three
  of the four things offered were taken; the fast scroller was deferred on size grounds.
- The transform is the risky one: experimental shared-element APIs, on the most used path in the
  application. It is isolated in SharedMedia.kt plus one wrapper and two call sites, so it reverts
  as a single commit if the device disagrees with it.
- Known: animateItem() on tiles and the shared element both govern placement and can disagree. If
  tiles jitter while scrolling or while density changes, dropping animateItem() from the tiles is
  a one-line change.
- Verified: build 35730482828 green on 808da9e. APK 11,552,503 bytes, SHA256
  d72c841bbed94734583ebd291afc9771308d835f45ce76bcfb6b624e4ec56b08 — 114,720 bytes more than
  0.1.11, mostly the shared transition machinery.
- The first build failed: the opt-in covered the two functions but not the composition local,
  whose own type is the experimental one. The sandbox cannot catch that class of mistake.
- Device: confirmed by the user. The transform looks right and the tiles do not jitter, so
  animateItem() and the shared element are getting along on this device.

## 0.1.11 viewer defects and a stable favorite key (confirmed on the device)
- Four things the device shows and the build cannot: video played over the user's music (no audio
  focus), the screen slept during a long video (PlayerView does not hold it), zooming magnified
  screen-sized pixels instead of loading real ones, and there was no drag-down to leave.
- The detail layer is capped at 4096 on the longest side. Uncapped, a fifty-megapixel frame is
  about two hundred megabytes of bitmap, which is a crash rather than a sharper picture.
- Drag to dismiss is photographs only. A PlayerView inside an AndroidView takes touches for
  itself, and intercepting on the initial pass would break the playback controls.
- Favorites moved off the content URI onto volume + relative path + name, closing the debt carried
  since 0.1.3. Migration keeps unmatched entries forever and only folds on a trustworthy read,
  which is the rule the 0.1.6 audit paid for.
- Verified: build 35707583676 green on 7b9561c. APK 11,437,783 bytes, SHA256
  287db6b39554721317a81ab1001b266ff1fb7fed9ede4c08faa23a87df393408 — 32,768 bytes more than
  0.1.10. FavoriteKeysTest and the rest of the suite pass.
- Device: confirmed by the user. Audio focus, the screen staying awake, the sharper zoom and the
  drag to dismiss all behave, and the favorite migration ran against real stored data without
  losing marks — which was the one thing here that could have cost something.

## 0.1.10 compact navigation bar (confirmed on the device)
- The user accepted 0.1.9 but called the bottom bar bulky. It was: 80dp plus the gesture inset, a
  tinted slab across the width, a label under every icon and a 64x32 indicator behind each one.
- Now 56dp on the black page, no slab, a smaller indicator on the selected tab only.
- Written as our own composable: NavigationBar fixes its height internally, and forcing it from
  outside risks clipping the label with no way to see that from here.
- The five-tap vault entry still lives on the Settings tab and is unchanged.
- Verified: build 35703348789 green on 0534e5a. APK 11,405,015 bytes, SHA256
  8fd684b0b822f80687703cc201f243fb378dc77bc6a7a73a3ddd1037f1d945ef — 32,772 bytes smaller
  than 0.1.9, since Material's NavigationBar and its item left the build with them.
- Device: confirmed by the user. The label reads inside 56dp, and the five-tap vault entry still
  registers through the new selectable.

## 0.1.9 Material 3 AMOLED (confirmed on the device)
- The user called the layout "нейрослоп" and asked for a modern Material 3 AMOLED design. The page
  is now true black and the tint lives on Material's tonal container ramp, not in a gradient behind
  the whole screen.
- Deliberate reversal, stated to the user before the work: the coloured gaps from 0.1.2 and the
  backdrop gradient from 0.1.5 are gone. Both were things the user had asked for; a gradient behind
  a nearly empty Избранное had become the loudest element on the screen.
- Settings rebuilt as Material settings groups. The statistics card and the explanatory paragraph
  under every control are gone. File counts left the screen titles and the date headers.
- Russian plurals fixed through fileCount(), which FormattingTest covers including the
  eleven-to-fourteen exception.
- Verified: build 35701853150 green on 6563716. APK 11,437,787 bytes, SHA256
  c2db3ea306d0669469666389db2bd552826acdbce174847cbaaadf4793cc55d9 — 17,783 bytes smaller
  than 0.1.8, which is the removed card, drawables and strings against the plural helper.
- Device: confirmed by the user. The black page reads better than the gradient it replaced.

## 0.1.8 trash screen (built green, confirmed working on the device)
- 0.1.6 made deletion recoverable through Android's trash but never listed it anywhere, so the user
  looked for a trash in the application and did not find one. Settings now has one.
- Restore and permanent erase both go back to the platform, which confirms them itself.
- Open question that the Android documentation does not settle: whether an application can list
  trashed items it does not own. If it cannot, the screen will look empty right after a deletion;
  its empty state says exactly that instead of claiming the trash is empty. Only the device answers
  this.
- Verified: build 35691402664 green on 21a3341. APK 11,455,570 bytes, SHA256
  01d0bdfac947aca5ca46bab26434a18cc3cf09e2c9d7624374a7816ef1b50c8d — 65,536 bytes more than
  0.1.7, which is the screen and the query behind it.
- Device: the user installed 0.1.8 and reported that everything works and their testing came out
  positive. That is a general confirmation of normal use, not a scenario-by-scenario report:
  they did not itemise which flows they exercised, so the adversarial cases below stay open
  until someone names them explicitly.

## 0.1.7 media type filter (confirmed on the device)
- Only media types a phone produces are read from MediaStore. A downloaded web project had put 127
  AVIF sprites into an album, where they did not even render. Filtering happens in the query, so
  such files never enter the application.
- The filter is an allowlist and therefore hides silently when an entry is missing; MediaTypesTest
  guards the formats a camera, screenshot or messenger produces.
- Verified: build 35689518604 green on fa85094. APK 11,390,034 bytes, SHA256
  97b0049b6017e07e8bddcddaa95b8917321ba143e33a6339a27451fde9bce44a — the same size as 0.1.6,
  since the change is a query filter rather than new code.
- Device: confirmed by the user. The album of web assets is gone and nothing real went with it.

## 0.1.6 privacy, trash, thumbnails and multi-select (built green, audited, confirmed on the device)
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
- Device: confirmed by the user, including the two that mattered — hiding a batch with the system
  dialog refused loses nothing, and a batch deletion is recoverable from the trash.

## Next
1. Nothing is waiting on a device check. Everything carried as a debt since 0.1.6 came back
   confirmed, including the cases that could have lost files.
2. Grid and viewer still hold the whole library in memory with no paging; the cursor column
   lookups repeat per row. Neither is felt at this size.
3. A fast scroller with a date bubble was offered and deferred: it earns its place from a few
   thousand files, not from a few hundred.

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
