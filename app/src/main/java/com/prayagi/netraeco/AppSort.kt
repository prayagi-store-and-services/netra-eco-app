package com.prayagi.netraeco

/**
 * Alphabetical order for every app list (owner rule, 16 Oct 2026): "Netra" is a title like
 * Mr/Mrs, so it is dropped before comparing. The rest of the name is compared letter by letter,
 * case-insensitively, so "Ecosystem" (Ec) comes before "Email" (Em). "NETRA x TRIKAL" drops the
 * "x" as well and sorts under T. A new app added to the catalog data slots into place on its own.
 */
object AppSort {
    fun key(name: String): String {
        var k = name.trim().lowercase()
        if (k.startsWith("netra")) k = k.removePrefix("netra").trimStart()
        if (k.startsWith("x ")) k = k.drop(2).trimStart()
        return k
    }

    /** Current apps use the latest actual publication time, newest first; unknown dates last. */
    fun current(rows: List<AppRow>): List<AppRow> = rows.sortedWith(
        compareByDescending<AppRow> { it.latest?.publishedAtMillis ?: Long.MIN_VALUE }
            .thenBy { key(it.app.name) }.thenBy { it.app.id }
    )

    fun <T> sorted(items: List<T>, name: (T) -> String): List<T> = items.sortedBy { key(name(it)) }
}
