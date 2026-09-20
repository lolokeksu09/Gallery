# Specification

Personal Gallery for the user's Android 13 phone. Kotlin, Jetpack Compose, Russian UI, black #000000 surfaces, portrait and landscape.

MVP: dated photo grid, albums, optional video, full-screen horizontal viewer, pinch/double-tap zoom, video playback, favorites, file details, sharing, Android-confirmed deletion, sorting and grid density.

Offline: no INTERNET or ACCESS_NETWORK_STATE, no accounts/analytics/cloud. No location, camera, microphone, notifications, broad storage or background service permissions. READ_MEDIA_IMAGES is requested on user action. READ_MEDIA_VIDEO only when enabling video. READ_MEDIA_VISUAL_USER_SELECTED supports limited access on Android 14+. Backup disabled.

MediaStore is the source of truth. Never copy originals or request write-all-files. File changes and permission changes refresh foreground results. Sharing passes read-only URI access to the application explicitly chosen by the user; that other app's networking is outside Gallery's control.

Deferred: editing, private vault, cloud, custom trash. Installed test build must be tested by user before stable status.
