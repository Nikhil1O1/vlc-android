package org.videolan.vlc.gui.music.tags

/**
 * The tags the editor can change. A null field means "leave as is", an empty string means "clear it".
 * Keys are TagLib property names.
 */
data class TagEdit(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val year: String? = null,
    val trackNumber: String? = null,
    val discNumber: String? = null,
    val cover: CoverEdit = CoverEdit.Keep,
) {
    /** Tag property name -> new value (empty list = remove the property). */
    fun toProperties(): Map<String, List<String>> = buildMap {
        title?.let { put("TITLE", listOfNotBlank(it)) }
        artist?.let { put("ARTIST", listOfNotBlank(it)) }
        album?.let { put("ALBUM", listOfNotBlank(it)) }
        albumArtist?.let { put("ALBUMARTIST", listOfNotBlank(it)) }
        genre?.let { put("GENRE", listOfNotBlank(it)) }
        year?.let { put("DATE", listOfNotBlank(it)) }
        trackNumber?.let { put("TRACKNUMBER", listOfNotBlank(it)) }
        discNumber?.let { put("DISCNUMBER", listOfNotBlank(it)) }
    }

    private fun listOfNotBlank(v: String) = v.trim().let { if (it.isEmpty()) emptyList() else listOf(it) }

    /** Returns a user-facing problem, or null if the edit is acceptable. */
    fun validate(): String? {
        year?.trim()?.takeIf { it.isNotEmpty() }?.let { if (!YEAR.matches(it)) return "Year must be 4 digits, e.g. 1994" }
        trackNumber?.trim()?.takeIf { it.isNotEmpty() }?.let { if (!NUMBER_OF_TOTAL.matches(it)) return "Track must be a number like 3 or 3/12" }
        discNumber?.trim()?.takeIf { it.isNotEmpty() }?.let { if (!NUMBER_OF_TOTAL.matches(it)) return "Disc must be a number like 1 or 1/2" }
        return null
    }

    companion object {
        private val YEAR = Regex("""\d{4}""")
        private val NUMBER_OF_TOTAL = Regex("""\d{1,4}(/\d{1,4})?""")
    }
}

sealed class CoverEdit {
    object Keep : CoverEdit()
    object Remove : CoverEdit()
    class Replace(val data: ByteArray, val mimeType: String) : CoverEdit()
}

/** Tag property names read for the editor form. */
fun Map<String, Array<String>>.first(key: String): String =
    this[key]?.firstOrNull()?.takeUnless { it.contains("(null)") }.orEmpty()

/** Four-digit year from a TagLib DATE value such as "1994", "1994-05-02". */
fun yearFromDate(date: String): String = Regex("""^\d{4}""").find(date.trim())?.value.orEmpty()
