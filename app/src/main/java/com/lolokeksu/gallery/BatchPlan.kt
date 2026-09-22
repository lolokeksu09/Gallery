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

/**
 * Moving favorites off the content URI, kept free of Android types so it can be tested.
 *
 * Favorites used to be stored against `content://media/external/images/media/1234`. MediaStore
 * reassigns that id whenever it rebuilds its index, so after a rescan every mark pointed at
 * nothing, or at a different photograph. The replacement is the file's own identity.
 *
 * The conversion is deliberately one-directional and never deletes. An entry that cannot be
 * matched stays in the legacy set for good, because the library reads empty with the permission
 * revoked and partial with a limited grant: treating "not found" as "gone" is exactly the mistake
 * that would have wiped every favorite in 0.1.6.
 */
object FavoriteMigration {
    /**
     * A volume, a relative path and a name identify a file on disk: two files cannot share all
     * three. Where MediaStore gives no relative path — it happens on some volumes — there is
     * nothing stable to key on, so the URI stays rather than collapsing every such file onto one
     * shared key.
     */
    fun stableKey(volume: String, path: String, name: String, uriKey: String): String =
        if (path.isBlank() || name.isBlank()) uriKey else "$volume|$path$name"

    /**
     * Folds the legacy URIs the library can account for into the stable set.
     *
     * [library] pairs each present file's URI key with its stable key. Returns the new stable set
     * and what is left of the legacy set; unmatched entries are returned untouched.
     */
    fun convert(
        legacy: Set<String>,
        favorites: Set<String>,
        library: List<Pair<String, String>>
    ): Pair<Set<String>, Set<String>> {
        if (legacy.isEmpty()) return favorites to legacy
        val matched = library.filter { it.first in legacy }
        if (matched.isEmpty()) return favorites to legacy
        return (favorites + matched.map { it.second }) to (legacy - matched.map { it.first }.toSet())
    }
}
