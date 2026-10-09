package org.pixeldroid.media_editor.photoEdit

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** File names for saved images. Pure functions, no Android dependencies. */
object FileNaming {
    /**
     * For edited photos: "<base>-1.<ext>", "<base>-2.<ext>", ... first one not in [usedNames].
     * A trailing "-<n>" of [originalName] is dropped first, so editing "photo-1" again gives
     * "photo-2". Only 1-3 digit numbers without leading zero count as such a number, and names
     * ending in a date ("2026-10-10") are left alone.
     */
    fun numberedName(originalName: String, usedNames: Set<String>, extension: String = "png"): String {
        val looksLikeDate = Regex("\\d{4}-\\d{2}-\\d{2}$").containsMatchIn(originalName)
        val base = (if (looksLikeDate) originalName
            else originalName.replace(Regex("-[1-9]\\d{0,2}$"), "")).ifBlank { "image" }
        var number = 1
        while ("$base-$number.$extension" in usedNames) number++
        return "$base-$number.$extension"
    }

    /** Today as "2026-10-09" */
    fun today(date: Date = Date()): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(date)

    /**
     * For collages: "<base>.<ext>" if free, otherwise "<base>-1.<ext>", "<base>-2.<ext>", ...
     */
    fun nameWithOptionalNumber(base: String, usedNames: Set<String>, extension: String = "png"): String {
        if ("$base.$extension" !in usedNames) return "$base.$extension"
        var number = 1
        while ("$base-$number.$extension" in usedNames) number++
        return "$base-$number.$extension"
    }
}
