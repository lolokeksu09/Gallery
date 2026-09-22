package com.lolokeksu.gallery

import org.junit.Assert.assertEquals
import org.junit.Test

class BatchPlanTest {
    @Test fun oneKindSharesAsThatKind() {
        assertEquals("image/jpeg", BatchPlan.shareType(listOf("image/jpeg", "image/jpeg")))
    }

    @Test fun mixedKindsShareAsWildcard() {
        assertEquals("*/*", BatchPlan.shareType(listOf("image/jpeg", "video/mp4")))
    }

    @Test fun emptyShareIsWildcard() {
        assertEquals("*/*", BatchPlan.shareType(emptyList()))
    }

    @Test fun aFullySelectedFavoriteSetClears() {
        assertEquals(setOf("c"), BatchPlan.nextFavorites(setOf("a", "b", "c"), setOf("a", "b")))
    }

    @Test fun aMixedSelectionFills() {
        assertEquals(setOf("a", "b", "c"), BatchPlan.nextFavorites(setOf("a"), setOf("b", "c", "a")))
    }

    @Test fun anUnfavoritedSelectionFills() {
        assertEquals(setOf("a", "b"), BatchPlan.nextFavorites(setOf("a"), setOf("b")))
    }

    @Test fun anEmptySelectionChangesNothing() {
        assertEquals(setOf("a"), BatchPlan.nextFavorites(setOf("a"), emptySet()))
    }
}
