# Specification

Personal Gallery for the user's Android 13 phone. Kotlin, Jetpack Compose, Russian UI, black #000000 surfaces, portrait and landscape.

MVP: dated photo grid, albums, optional video, full-screen horizontal viewer, pinch/double-tap zoom, video playback, favorites, file details, sharing, Android-confirmed deletion, sorting and grid density.

Offline: no INTERNET or ACCESS_NETWORK_STATE, no accounts/analytics/cloud. No location, camera, microphone, notifications, broad storage or background service permissions. READ_MEDIA_IMAGES and READ_MEDIA_VIDEO are requested together on user action. Both media types are enabled by default; old video-off preference is ignored. Respect denial and individual permission grants. READ_MEDIA_VISUAL_USER_SELECTED supports limited access on Android 14+. Backup disabled.

0.1.2 user feedback: photo grid tiles need visible gaps and rounded corners instead of touching each other; a pinch on the photo grid changes tile size (2-5 columns, stored in the same density setting); video playback goes edge-to-edge full screen (application bars and system bars hidden) about five seconds after playback starts, and a tap brings the controls back; the settings tab is rebuilt as cards with a library overview, a visual grid-density picker, sorting and access rows.
Signing: the user's phone runs an Android patch that skips APK signature verification, so a changing debug certificate no longer blocks in-place updates on that device. This is the user's statement about their own device, not a verified property of the build.

0.1.1 user feedback: compact adaptive album tiles (100dp minimum, 3 columns on ordinary phone widths); smaller labels; settings only contain grid density and access controls, no technical/marketing descriptions or version label.
The first 0.1.1 launch requests combined access once if full access is missing, preserving existing Android14 limited selection. Refusal is respected; subsequent requests are explicit via the access button. Only onboarding-shown state is persisted, never permission grants.

MediaStore is the source of truth. Never copy originals or request write-all-files. File changes and permission changes refresh foreground results. Sharing passes read-only URI access to the application explicitly chosen by the user; that other app's networking is outside Gallery's control.

Private vault (0.1.4): hidden behind five taps on the already open Settings tab; no other entry point exists.
Files are encrypted with AES-GCM under a random data key that is itself wrapped by a PBKDF2 key from the
user's password, so a password change rewraps one blob instead of rewriting media. Media is stored as
independent 1 MiB frames, each binding its index as associated data, so reordering, splicing or truncation
fail to decrypt rather than returning wrong bytes. Import encrypts, decrypts the copy again and compares
SHA-256 before the platform delete dialog is shown for the original; a refused deletion discards the copy.
Restore writes the file back to MediaStore. Viewing decrypts into the application cache, which is wiped
whenever the vault locks; the vault locks itself when the application leaves the foreground. There is no
password recovery, and the user is told so before the vault is created.

Deferred: editing, cloud, custom trash. Installed test build must be tested by user before stable status.
