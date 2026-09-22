# Changelog

Version-to-version history of the Gallery application. Every version bump adds an entry here.
Sizes and hashes come from the CI build that produced that version; where a build was not measured,
this file says so instead of guessing.

## 0.1.9 — versionCode 10

Not built yet; this entry is completed once CI reports the size and hash.

Changed — Material 3 on AMOLED
- The page is true black on every theme. The backdrop gradient is gone, and with it the tinted gaps
  between photographs: the tint now lives only where interface is actually drawn. This reverses the
  coloured gaps added in 0.1.2 and the gradient added in 0.1.5, deliberately, because a gradient
  behind a nearly empty screen became the loudest thing on it.
- Themes are now an accent plus Material's tonal container ramp (surfaceContainerLow / Container /
  High) tinted towards that accent's hue. All five keep their names — Аметист, Закат, Океан, Мята,
  Чернила — and their accents moved to Material's tone 80, which is brighter on black.
- The colour scheme fills Material's container roles instead of four of them, so menus, dialogs,
  sheets and the navigation bar get their tones from the theme rather than from defaults.
- The top bar is one line and transparent over the feed, taking the container tone only once
  content scrolls under it, which is Material's own behaviour for a bar on a dark surface.

Changed — the layout that read as generated
- File counts left the screen titles and the date headers. A count now appears where it is acted
  on: an album tile, the trash, the vault.
- Settings lost the statistics card and every explanatory paragraph under a control, and its five
  identical bordered cards became Material settings groups: a quiet accent label over rows on the
  container tone. The sections are Вид, Сортировка, Файлы, Доступ.

Fixed
- Russian plurals. The interface said "1 файлов" and "3 файлов" everywhere because the number was
  pasted into one fixed form. fileCount() picks файл / файла / файлов, including the eleven-to-
  fourteen exception that the last digit alone gets wrong, and FormattingTest covers it.

Removed
- ic_grid and ic_palette: the settings sections no longer carry icons.

## 0.1.8 — versionCode 9

Build: https://github.com/lolokeksu09/Gallery/actions/runs/35691402664 (commit 21a3341).
APK 11,455,570 bytes, SHA256 01d0bdfac947aca5ca46bab26434a18cc3cf09e2c9d7624374a7816ef1b50c8d.
Static checks, unit tests, Android Lint, assembleDebug and the APK permission audit passed.
That is 65,536 bytes more than 0.1.7 — the trash screen and the repository query behind it.
Not run on a device.

Added
- A trash screen, in Settings under "Файлы". 0.1.6 changed deletion to createTrashRequest, so files
  became recoverable for thirty days, but nothing in the application listed them: the trash was
  Android's and only reachable from the Files app or the system gallery. Now it is listed here.
- Tap to select, "Все" to take everything, then restore or erase permanently. Both hand the work
  back to Android, which asks for its own confirmation; erasing asks once more first, because that
  one cannot be undone.
- The listing is loaded only while the screen is open and dropped when it closes.

Known limitation, stated in the empty state
- Android may withhold trashed items that belong to another application, and an empty result looks
  identical to an empty trash from inside the app. The empty state says so and points at the Files
  app rather than claiming the trash is empty. Whether this device shows other apps' trashed files
  is not settled by the Android documentation; it can only be answered by looking.

## 0.1.7 — versionCode 8

Build: https://github.com/lolokeksu09/Gallery/actions/runs/35689518604 (commit fa85094).
APK 11,390,034 bytes, SHA256 97b0049b6017e07e8bddcddaa95b8917321ba143e33a6339a27451fde9bce44a.
Static checks, unit tests, Android Lint, assembleDebug and the APK permission audit passed.
Byte for byte the same size as 0.1.6: the change is a query filter, not new code paths.
Not run on a device.

Changed
- The gallery only shows media types a phone actually produces. MediaStore indexes every image and
  video on the device, so a downloaded web project put 127 AVIF sprites of 128x128 into an album,
  where they did not even render. The filter is a MIME allowlist applied in the MediaStore query, so
  those files never enter the application rather than being hidden in the interface.
- Kept: jpeg, jpg, png, heic, heif, webp, gif, bmp, dng for photos; mp4, 3gpp, 3gpp2, webm, mkv,
  quicktime, mpeg, mp2t, avi for video. Everything a camera, a screenshot or a messenger produces.
- Excluded by consequence: avif, svg, ico and anything else not on the list.
- MediaTypesTest guards the list, because an allowlist hides real photos silently when an entry is
  missing. If something real disappears, its type from the details dialog is a one-line addition.

## 0.1.6 — versionCode 7

Build: https://github.com/lolokeksu09/Gallery/actions/runs/35532400821 (commit 03b1367).
APK 11,390,034 bytes, SHA256 94da1205999462e11d5818f70fac88b013967c1f82b2a81c174e1d2f0943cb0f.
Static checks, unit tests, Android Lint, assembleDebug and the APK permission audit passed.

That is 1,131,257 bytes more than 0.1.3, the last release measured. The growth is the vault, the
theme system and multi-select, not a packaging regression: nothing about shrinking changed between
the two. Nothing in 0.1.6 has run on a device.

Added
- Multi-select in the photo grid. A long press starts it, a tap extends it, back leaves it. The top
  bar becomes "N выбрано" with share, favorite, hide and delete.
- Batch actions act once instead of per file: one system dialog covers the whole selection, sharing
  uses ACTION_SEND_MULTIPLE, and favorites toggle as a group (an already fully favorited selection
  clears). BatchPlanTest covers the share type and the group toggle.
- Hiding a selection encrypts and verifies each file separately, then asks once to delete the
  verified originals. Refusing that dialog removes every copy made for the batch, so a file is never
  lost from both places. Progress is a bare counter, never a file name.

Changed
- FLAG_SECURE is held while the vault is open or unlocked. The unlocked vault used to be captured
  into the recent-apps snapshot, which survives the screen lock, and was screenshotable, recordable
  and castable. It is not held outside the vault, so ordinary screenshots still work.
- The wrong-password count is stored, so force-stopping the application no longer resets the growing
  delay it earned.
- Deleting from the gallery uses createTrashRequest, so a deletion is recoverable from the system
  trash for 30 days. Removing the original after a vault import deliberately stays a real delete: a
  trashed original would still be listed in the system trash.
- Video tiles come from MediaStore's own thumbnails instead of decoding a frame out of the original
  file on every scroll. Photos stay with Coil, which remains the fallback.
- tools/verify.sh resolves every repository.name(...) call against a declared function.

Fixed
- The favorites count in settings only ever grew, because keys were never removed when a file went
  away. The count is now taken against the library rather than the stored set. Pruning the set was
  tried first and reverted: with access revoked or only one media permission granted the library
  reads empty or partial, and pruning would have deleted favorites for files that still exist.
- A rotation while a hide was pending raised a second platform delete dialog for the same batch;
  cancelling one while confirming the other would have lost those files from both places.
- Locking the vault mid-batch left encrypted copies behind while their originals were still in the
  gallery, because the running import was not cancelled. It is cancelled and cleans up after itself.
- The encryption scrim did not take touches, so the grid and the selection bar stayed live
  underneath it, and taps around the vault password screen reached the settings page behind it.
- Both thumbnail caches were bounded by entry count rather than bytes, so a 512px preview at about
  a megabyte each could hold well over a hundred megabytes.

Known limitations, not fixed here
- Favorites still use the content URI as their key, so they are lost when MediaStore re-indexes and
  when a file is restored from the vault (restore creates a new row). A stable key needs a migration.
  The stored set is never pruned either, so it grows slowly; only the counter is corrected.
- Filtering, sorting and date grouping still run in composition on the main thread. Fine at this
  library size; revisit if it grows or lag appears.

## 0.1.5 — versionCode 6

Not built yet; this entry is completed once CI reports the size and hash.

Changed
- Hiding a file is silent. A message naming the file or the vault after a move would tell anyone
  looking at the screen that a vault exists, so success and refusal say nothing, the progress spinner
  carries no label, and the viewer button is described as "Скрыть".
- New colour system. A theme defines accent, backdrop gradient, chrome, card, border and muted colours
  together, so surfaces shift as one palette instead of an accent dropped on flat black. Surfaces are
  deep but tinted; pure black is kept for what sits directly behind media and for scrims.
- Five themes, chosen in settings and applied immediately: Аметист (default), Закат, Океан, Мята (the
  former palette, kept so nothing is lost) and Чернила.
- The navigation bar indicator, the settings hero card and every selected state now use the accent
  rather than Material's default grey.
- Added a palette vector drawable for the new settings section.

## 0.1.4 — versionCode 5

Not built yet; this entry is completed once CI reports the size and hash.

Added
- Private vault, reachable only by tapping the already open Settings tab five times. Nothing else in the
  interface hints that it exists, and the "move to vault" button appears in the viewer only while the
  vault is unlocked.
- Files are encrypted with AES-GCM. A random data key encrypts the media and is wrapped with a PBKDF2
  key derived from the password, so changing the password rewraps one small blob instead of rewriting
  every file. Media is written as independent 1 MiB frames, each binding its frame index as associated
  data, so a reordered, spliced or truncated file fails to decrypt instead of returning wrong bytes.
- Import never destroys anything on its own: the encrypted copy is decrypted again and its SHA-256
  compared with the source, and only then does Android ask to delete the original. Refusing that dialog
  discards the vault copy, so a file is never in neither place.
- Restore writes a file back into MediaStore; deleting from the vault asks for confirmation and says
  plainly that there is nothing left to restore from.
- The vault locks when the application leaves the foreground, dropping the key and wiping every
  decrypted copy from the cache. Repeated wrong passwords add a growing delay.
- Encrypted per-item thumbnails, so the vault grid never decrypts whole photos or videos.
- VaultCryptoTest covers frame boundaries, wrong keys, altered bytes, truncation and reordering.

## 0.1.3 — versionCode 4

Build: https://github.com/lolokeksu09/Gallery/actions/runs/35518294962 (commit 393baf5).
APK 10,258,777 bytes, SHA256 7f1b42cee640751ef2877f86b32e44c072a83b40981627d9602b2acf2e859d50.
Unit tests, Android Lint, assembleDebug and the APK permission audit passed. Not run on a device.

Added
- Faint green-tinted backdrop under the photo and album grids, so the gaps between photos read as
  colour instead of flat black. Bars, cards and thumbnails stay near black for AMOLED.
- Motion throughout: tab and album switches, the viewer opening and closing over the home screen,
  eased double-tap zoom, sliding viewer bars, animated grid density changes, thumbnail crossfade and
  animated selection colours in settings.
- Unit tests for hour-long and negative durations.
- CI prints APK size and SHA256.
- tools/verify.sh: SDK-free checks for malformed XML, AppCompat-only attribute references,
  unresolved R.drawable references, icons outside material-icons-core and loss of the offline
  manifest guarantee. Runs as the first CI step and locally, where no Android SDK is reachable.
- Releases: the Release APK workflow builds and publishes the APK as a prerelease with a permanent
  download link. Run it from the Actions tab with a version, or push a v* tag. The tag is created
  on GitHub at the built commit, because this development sandbox cannot push tag refs.

Changed
- Dropped androidx.compose.material-icons-extended; four vector drawables in res/drawable cover the
  icons it supplied.
- Disabled unused build features, excluded packaging metadata, removed APK dependency metadata,
  limited resources to ru/en, enabled the Gradle configuration cache and in-process Kotlin compilation.
  Measured effect: APK 0.22% smaller than 0.1.1 and no measurable build-time change on a feature
  branch, where the Actions cache is read-only. Kept as correct and free, not as a proven win.

Fixed
- Android 14 grants READ_MEDIA_VISUAL_USER_SELECTED alongside full access, so the application wrongly
  reported limited access and offered "select more files" when access was already full.
- refresh() cleared the media list before reloading, so every resume and every MediaStore change
  blanked the grid and flashed a spinner.
- Cancelling the system delete dialog closed the viewer.
- formatDuration rendered a 90-minute video as "90:00" instead of "1:30:00".
- The open viewer kept the media snapshot taken when it opened, so refreshes never reached it.
- Reopening the viewer during its exit animation reused the previous pager state and ignored the
  tapped photo.
- A fast pinch could lose a step by reading a column count still in flight through DataStore.
- The filtered and sorted list was memoised three times with identical keys.
- GalleryViewModel.onCleared did not call super.
- Vector drawables referenced ?attr/colorControlNormal, an AppCompat attribute absent from this
  project, which broke resource linking in 0.1.3 development before release.

## 0.1.2 — versionCode 3

Build: https://github.com/lolokeksu09/Gallery/actions/runs/35516392490 (commit 62ebd2c).
Tests, lint, assembleDebug and the permission audit passed. APK not downloaded, so no size or hash
was recorded for this version. Not run on a device.

Added
- Two-finger pinch on the photo grid changes tile size across 2-5 columns, sharing the stored density
  setting with the settings tab.
- Video plays on open and goes edge-to-edge about five seconds later: application bars and system bars
  hide together, and a tap or back restores them.
- Settings rebuilt as cards: library overview, grid density picker with miniature layout previews,
  sorting, access status indicator and access action rows.

Changed
- Photo grid tiles gained gaps (5-10dp, scaled with column count) and rounded corners instead of
  touching each other; date headers show a file count.
- The viewer replaced its Scaffold with an overlay layout so content runs under the bars.
- CI builds pushes on claude/** branches.
- Added androidx.core:core-ktx for WindowInsetsControllerCompat.

## 0.1.1 — versionCode 2

Build: https://github.com/lolokeksu09/Gallery/actions/runs/35515287324 (commit 00483ec).
APK 10,281,374 bytes, SHA256 85e4f3f973919266a1b371ee97486427b03313f4a8cee248217e87562c275c6e.

Changed
- Compact adaptive album cards; smaller labels.
- Settings reduced to grid density and access controls; technical text and the version label removed.
- Photo and video libraries enabled together by default; both permissions requested together on user
  action, and the old video-off preference no longer hides video.
- CI optimised: Gradle task cache, two workers, SDK reuse, cancellation of superseded builds and
  documentation-only push filtering, with R8 and all checks kept.

Notes
- The certificate differed from 0.1.0, so an in-place update was not possible at the time.

## 0.1.0 — versionCode 1

Build: https://github.com/lolokeksu09/Gallery/actions/runs/35514670112 (commit 6c91585).
APK 10,330,530 bytes, SHA256 c2c1d149325d80424a3424df1b3bf2a9d514dd0191b02dca43b2f42d6cbb40ae.
R8 and resource shrinking reduced the package from 67,084,094 bytes.

Added
- First offline AMOLED gallery: photo grid with day groups, albums, favorites, sorting and density
  settings.
- MediaStore access with optional video and Android 14 limited selection.
- Viewer with zoom, Media3 playback, sharing, file details and system-confirmed deletion.
- Offline manifest and APK permission audit in GitHub Actions; duration unit tests.
