package com.bdavidgm.notas

import com.bdavidgm.notas.ui.util.NOTE_LINK_BOUNDARY
import com.bdavidgm.notas.ui.util.ensureRichLinkBoundaries
import com.bdavidgm.notas.ui.util.removeRichLinkBoundaries
import com.bdavidgm.notas.ui.util.stripInternalNoteLinks
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteLinkTest {

    @Test
    fun ensureRichLinkBoundaries_addsOneInvisibleBoundaryToEveryLink() {
        val input = "Antes [interna](notas://nota/abc) y [web](https://example.com) después"
        val boundary = NOTE_LINK_BOUNDARY

        assertEquals(
            "Antes [interna](notas://nota/abc)$boundary y " +
                "[web](https://example.com)$boundary después",
            ensureRichLinkBoundaries(input),
        )
    }

    @Test
    fun ensureRichLinkBoundaries_isIdempotentAndDoesNotTouchImages() {
        val boundary = NOTE_LINK_BOUNDARY
        val input = "[nota](notas://nota/abc)$boundary ![foto](file:///foto.jpg)"

        assertEquals(input, ensureRichLinkBoundaries(ensureRichLinkBoundaries(input)))
    }

    @Test
    fun exportedTextDropsInternalTargetAndTechnicalBoundaries() {
        val boundary = NOTE_LINK_BOUNDARY
        val input = "Ver [otra nota](notas://nota/abc)$boundary y continuar"

        assertEquals("Ver otra nota y continuar", stripInternalNoteLinks(input))
        assertEquals(
            "Ver [otra nota](notas://nota/abc) y continuar",
            removeRichLinkBoundaries(input),
        )
    }
}
