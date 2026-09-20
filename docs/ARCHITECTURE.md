# Architecture

One app module. Compose UI -> GalleryViewModel -> MediaRepository -> MediaStore. Coroutines perform metadata reads off the main thread. ViewModel exposes StateFlow; UI observes with lifecycle awareness. Image decoding uses Coil without its optional network module. Video uses Media3, paused on background and released on disposal.

DataStore stores small settings and favorite URI sets; no Room/Hilt/navigation framework is needed for this initial small app. This deliberately simplifies the initial proposal. A content observer refreshes metadata, debounced; no background service. Metadata is currently loaded as a list; bitmap previews are lazy and downsampled. Paging is a follow-up if very large libraries prove slow.

Toolchain: JDK17, Gradle8.11.1, AGP8.9.2, Kotlin2.1.20, SDK35/min33, pinned dependencies. GitHub Actions installs SDK and Gradle, runs tests/lint/assembleDebug, then audits actual APK permissions and uploads an artifact. No permanent release signing key exists yet; CI debug signing does not guarantee update compatibility between runs. Plan persistent private signing before stable distribution.

Android references:
- https://developer.android.com/about/versions/14/changes/partial-photo-video-access
- https://developer.android.com/build/releases/agp-8-9-0-release-notes
