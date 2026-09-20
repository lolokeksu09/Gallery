package com.lolokeksu.gallery

/**
 * The decisions behind the batch actions, kept free of Android types so they can be tested.
 * Both are easy to get subtly wrong and neither needs a device to verify.
 */
object BatchPlan {
    /** A share of mixed kinds has to fall back to a wildcard, or receivers filter the set wrongly. */
    fun shareType(mimes: List<String>): String = mimes.distinct().singleOrNull() ?: "*/*"

    /**
     * Toggling a selection as a whole: a set that is already entirely favorited clears, anything
     * else fills. Without the "already all" case, a mixed selection could never be cleared.
     */
    fun nextFavorites(current: Set<String>, keys: Set<String>): Set<String> = when {
        keys.isEmpty() -> current
        current.containsAll(keys) -> current - keys
        else -> current + keys
    }
}
