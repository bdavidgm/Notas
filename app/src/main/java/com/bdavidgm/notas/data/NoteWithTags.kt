package com.bdavidgm.notas.data

import com.bdavidgm.notas.data.local.NoteEntity
import com.bdavidgm.notas.data.local.NoteTagJoinRow
import com.bdavidgm.notas.data.local.TagEntity

data class NoteWithTags(
    val note: NoteEntity,
    val tags: List<TagEntity>,
)

internal fun List<NoteTagJoinRow>.toNoteWithTagsList(): List<NoteWithTags> {
    if (isEmpty()) return emptyList()
    return groupBy { it.noteId }
        .entries
        .sortedByDescending { it.value.first().updatedAtMillis }
        .map { (_, rows) ->
            val r0 = rows.first()
            val note = NoteEntity(
                id = r0.noteId,
                title = r0.title,
                content = r0.content,
                createdAtMillis = r0.createdAtMillis,
                updatedAtMillis = r0.updatedAtMillis,
            )
            val tags = rows.mapNotNull { r ->
                if (r.tagId < 0L) return@mapNotNull null
                TagEntity(id = r.tagId, name = r.tagName)
            }.distinctBy { it.id }.sortedBy { it.name.lowercase() }
            NoteWithTags(note = note, tags = tags)
        }
}

internal fun List<NoteTagJoinRow>.toSingleNoteWithTags(): NoteWithTags? {
    if (isEmpty()) return null
    return toNoteWithTagsList().firstOrNull()
}
