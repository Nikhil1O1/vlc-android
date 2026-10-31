package org.videolan.vlc.gui.music.tags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TagEditTest {
    @Test fun nullFieldsAreLeftOut() {
        assertEquals(emptyMap<String, List<String>>(), TagEdit().toProperties())
    }

    @Test fun blankClearsAndValuesAreTrimmed() {
        val p = TagEdit(title = "  Hello ", genre = "  ").toProperties()
        assertEquals(listOf("Hello"), p["TITLE"])
        assertEquals(emptyList<String>(), p["GENRE"])
    }

    @Test fun yearValidation() {
        assertNull(TagEdit(year = "1994").validate())
        assertNull(TagEdit(year = "").validate())
        assertNotNull(TagEdit(year = "94").validate())
        assertNotNull(TagEdit(year = "abcd").validate())
    }

    @Test fun trackAndDiscValidation() {
        assertNull(TagEdit(trackNumber = "3/12", discNumber = "1").validate())
        assertNotNull(TagEdit(trackNumber = "3/").validate())
        assertNotNull(TagEdit(discNumber = "x").validate())
    }

    @Test fun yearFromDate() {
        assertEquals("1994", yearFromDate("1994-05-02"))
        assertEquals("", yearFromDate("unknown"))
    }
}
