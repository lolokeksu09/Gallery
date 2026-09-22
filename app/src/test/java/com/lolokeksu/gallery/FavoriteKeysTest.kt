package com.lolokeksu.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteKeysTest {
    @Test fun keyIsVolumePathAndName() {
        assertEquals(
            "external_primary|DCIM/Camera/IMG_001.jpg",
            FavoriteMigration.stableKey("external_primary", "DCIM/Camera/", "IMG_001.jpg", "content://x/1")
        )
    }

    @Test fun sameNameOnDifferentVolumesStaysDistinct() {
        val internal = FavoriteMigration.stableKey("external_primary", "DCIM/Camera/", "IMG_1.jpg", "content://x/1")
        val card = FavoriteMigration.stableKey("0000-1111", "DCIM/Camera/", "IMG_1.jpg", "content://x/2")
        assertTrue(internal != card)
    }

    // Without the fallback every file on such a volume would collapse onto one shared key and
    // favoriting one would favorite them all.
    @Test fun blankPathFallsBackToTheUri() {
        assertEquals("content://x/7", FavoriteMigration.stableKey("external_primary", "", "IMG.jpg", "content://x/7"))
    }

    @Test fun blankNameFallsBackToTheUri() {
        assertEquals("content://x/8", FavoriteMigration.stableKey("external_primary", "DCIM/", "", "content://x/8"))
    }

    @Test fun convertFoldsWhatTheLibraryAccountsFor() {
        val (favorites, legacy) = FavoriteMigration.convert(
            legacy = setOf("content://x/1", "content://x/2"),
            favorites = emptySet(),
            library = listOf("content://x/1" to "v|a/one.jpg", "content://x/2" to "v|a/two.jpg")
        )
        assertEquals(setOf("v|a/one.jpg", "v|a/two.jpg"), favorites)
        assertTrue(legacy.isEmpty())
    }

    @Test fun convertKeepsWhatItCannotMatch() {
        val (favorites, legacy) = FavoriteMigration.convert(
            legacy = setOf("content://x/1", "content://gone/9"),
            favorites = emptySet(),
            library = listOf("content://x/1" to "v|a/one.jpg")
        )
        assertEquals(setOf("v|a/one.jpg"), favorites)
        assertEquals(setOf("content://gone/9"), legacy)
    }

    // The library reads empty with the permission revoked. Nothing may be lost in that case.
    @Test fun emptyLibraryLosesNothing() {
        val (favorites, legacy) = FavoriteMigration.convert(
            legacy = setOf("content://x/1"),
            favorites = setOf("v|a/kept.jpg"),
            library = emptyList()
        )
        assertEquals(setOf("v|a/kept.jpg"), favorites)
        assertEquals(setOf("content://x/1"), legacy)
    }

    @Test fun convertKeepsExistingStableEntries() {
        val (favorites, _) = FavoriteMigration.convert(
            legacy = setOf("content://x/1"),
            favorites = setOf("v|a/already.jpg"),
            library = listOf("content://x/1" to "v|a/one.jpg")
        )
        assertEquals(setOf("v|a/already.jpg", "v|a/one.jpg"), favorites)
    }

    @Test fun runningItTwiceChangesNothingTheSecondTime() {
        val library = listOf("content://x/1" to "v|a/one.jpg")
        val first = FavoriteMigration.convert(setOf("content://x/1"), emptySet(), library)
        val second = FavoriteMigration.convert(first.second, first.first, library)
        assertEquals(first.first, second.first)
        assertEquals(first.second, second.second)
    }
}
