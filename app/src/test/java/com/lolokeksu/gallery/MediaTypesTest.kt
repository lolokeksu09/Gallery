package com.lolokeksu.gallery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The type filter is an allowlist, so a missing entry hides real photos without saying anything.
 * These guard the formats a phone actually produces.
 */
class MediaTypesTest {
    @Test fun everythingACameraOrScreenshotProducesIsKept() {
        for (type in listOf("image/jpeg", "image/png", "image/heic", "image/heif", "image/webp")) {
            assertTrue("$type must be shown", type in MediaTypes.photos)
        }
    }

    @Test fun everythingAPhoneRecordsIsKept() {
        for (type in listOf("video/mp4", "video/3gpp", "video/webm", "video/quicktime")) {
            assertTrue("$type must be shown", type in MediaTypes.videos)
        }
    }

    @Test fun webAssetFormatsAreNotShown() {
        for (type in listOf("image/avif", "image/svg+xml", "image/x-icon", "image/vnd.microsoft.icon")) {
            assertFalse("$type must stay out of the gallery", type in MediaTypes.photos)
        }
    }

    @Test fun theTwoListsDoNotOverlapAndAreLowercase() {
        assertTrue(MediaTypes.photos.none { it in MediaTypes.videos })
        assertTrue(MediaTypes.photos.all { it == it.lowercase() && it.startsWith("image/") })
        assertTrue(MediaTypes.videos.all { it == it.lowercase() && it.startsWith("video/") })
    }

    @Test fun ofPicksTheRightList() {
        assertTrue(MediaTypes.of(video = false) === MediaTypes.photos)
        assertTrue(MediaTypes.of(video = true) === MediaTypes.videos)
    }
}
