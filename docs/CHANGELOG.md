# Changelog

Version-to-version history of the Gallery application. Every version bump adds an entry here.
Sizes and hashes come from the CI build that produced that version; where a build was not measured,
this file says so instead of guessing.

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
- Tagging v* builds the APK and publishes it as a prerelease with a permanent download link.

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
