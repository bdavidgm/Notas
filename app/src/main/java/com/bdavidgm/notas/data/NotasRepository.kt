package com.bdavidgm.notas.data

import android.content.Context
import android.net.Uri
import com.bdavidgm.notas.data.local.NoteEntity
import com.bdavidgm.notas.data.local.NoteImageEntity
import com.bdavidgm.notas.data.local.NoteTagCrossRef
import com.bdavidgm.notas.data.local.NotasDao
import com.bdavidgm.notas.data.local.TagEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class NotasRepository(
    private val dao: NotasDao,
    private val appContext: Context,
) {

    fun observeNotesMatchingSearch(searchPattern: String): Flow<List<NoteWithTags>> =
        dao.observeNoteTagJoinRowsBySearch(searchPattern).map { it.toNoteWithTagsList() }

    fun observeNoteWithTags(noteId: Long): Flow<NoteWithTags?> =
        dao.observeNoteTagJoinRowsForNote(noteId).map { it.toSingleNoteWithTags() }

    fun observeImages(noteId: Long) = dao.observeImagesForNote(noteId)

    suspend fun createBlankNote(): Long {
        val now = System.currentTimeMillis()
        return dao.insertNote(
            NoteEntity(
                title = "",
                content = "",
                createdAtMillis = now,
                updatedAtMillis = now,
            ),
        )
    }

    suspend fun updateNote(note: NoteEntity) {
        dao.updateNote(note)
    }

    suspend fun getNote(id: Long): NoteEntity? = dao.getNote(id)

    suspend fun persistDraftIfChanged(noteId: Long, title: String, content: String) {
        val current = dao.getNote(noteId) ?: return
        val t = title.trimEnd()
        if (t == current.title && content == current.content) return
        dao.updateNote(
            current.copy(
                title = t,
                content = content,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deleteNote(noteId: Long) {
        val images = dao.observeImagesForNote(noteId).first()
        withContext(Dispatchers.IO) {
            images.forEach { File(it.storedPath).delete() }
            File(appContext.filesDir, "note_images/$noteId").deleteRecursively()
        }
        dao.deleteNoteById(noteId)
    }

    suspend fun addTagToNote(noteId: Long, rawName: String) {
        val name = rawName.trim()
        if (name.isEmpty()) return
        dao.insertTag(TagEntity(name = name))
        val tag = dao.getTagByName(name) ?: return
        dao.linkTagToNote(NoteTagCrossRef(noteId = noteId, tagId = tag.id))
    }

    suspend fun removeTagFromNote(noteId: Long, tagId: Long) {
        dao.unlinkTag(noteId, tagId)
    }

    suspend fun copyGalleryImagesToNote(noteId: Long, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val existing = dao.observeImagesForNote(noteId).first()
        var order = existing.maxOfOrNull { it.sortOrder }?.plus(1) ?: 0
        withContext(Dispatchers.IO) {
            val dir = File(appContext.filesDir, "note_images/$noteId").apply { mkdirs() }
            for (uri in uris) {
                val dest = File(dir, "${UUID.randomUUID()}.jpg")
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                } ?: continue
                dao.insertNoteImage(
                    NoteImageEntity(
                        noteId = noteId,
                        storedPath = dest.absolutePath,
                        sortOrder = order++,
                    ),
                )
            }
        }
    }

    suspend fun deleteImage(imageId: Long, storedPath: String) {
        withContext(Dispatchers.IO) {
            File(storedPath).delete()
        }
        dao.deleteImage(imageId)
    }

    companion object {
        fun likePattern(raw: String): String {
            if (raw.isBlank()) return "%"
            val safe = raw.replace('%', ' ').replace('_', ' ')
            return "%$safe%"
        }
    }
}
