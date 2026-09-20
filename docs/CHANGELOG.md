# Changelog

Version-to-version history of the Gallery application. Every version bump adds an entry here.
Sizes and hashes come from the CI build that produced that version; where a build was not measured,
this file says so instead of guessing.

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
