package com.bdavidgm.notas.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NotasDao {

    @Query(
        """
        SELECT n.id AS noteId, n.title AS title, n.content AS content,
               n.createdAtMillis AS createdAtMillis, n.updatedAtMillis AS updatedAtMillis,
               IFNULL(t.id, -1) AS tagId, IFNULL(t.name, '') AS tagName
        FROM notes n
        LEFT JOIN note_tags nt ON n.id = nt.noteId
        LEFT JOIN tags t ON nt.tagId = t.id
        WHERE (n.title LIKE :searchPattern OR n.content LIKE :searchPattern)
        ORDER BY n.updatedAtMillis DESC, t.name COLLATE NOCASE ASC
        """,
    )
    fun observeNoteTagJoinRowsBySearch(searchPattern: String): Flow<List<NoteTagJoinRow>>

    @Query(
        """
        SELECT n.id AS noteId, n.title AS title, n.content AS content,
               n.createdAtMillis AS createdAtMillis, n.updatedAtMillis AS updatedAtMillis,
               IFNULL(t.id, -1) AS tagId, IFNULL(t.name, '') AS tagName
        FROM notes n
        LEFT JOIN note_tags nt ON n.id = nt.noteId
        LEFT JOIN tags t ON nt.tagId = t.id
        WHERE n.id = :id
        ORDER BY t.name COLLATE NOCASE ASC
        """,
    )
    fun observeNoteTagJoinRowsForNote(id: Long): Flow<List<NoteTagJoinRow>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNote(id: Long): NoteEntity?

    @Insert
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity): Int

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long): Int

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
    fun observeAllTags(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getTagByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkTagToNote(ref: NoteTagCrossRef): Long

    @Query("DELETE FROM note_tags WHERE noteId = :noteId AND tagId = :tagId")
    suspend fun unlinkTag(noteId: Long, tagId: Long): Int

    /** Borra la etiqueta; las uniones en note_tags caen por CASCADE. */
    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun deleteTagById(tagId: Long): Int

    @Insert
    suspend fun insertNoteImage(image: NoteImageEntity): Long

    @Query("DELETE FROM note_images WHERE noteId = :noteId AND storedPath = :storedPath")
    suspend fun deleteImageByPath(noteId: Long, storedPath: String): Int

    @Query("SELECT * FROM note_images WHERE noteId = :noteId ORDER BY sortOrder ASC, id ASC")
    fun observeImagesForNote(noteId: Long): Flow<List<NoteImageEntity>>

    @Query("SELECT * FROM note_images WHERE noteId = :noteId ORDER BY sortOrder ASC, id ASC")
    suspend fun getImagesForNoteExport(noteId: Long): List<NoteImageEntity>

    @Query(
        """
        SELECT n.id AS noteId, n.title AS title, n.content AS content,
               n.createdAtMillis AS createdAtMillis, n.updatedAtMillis AS updatedAtMillis,
               IFNULL(t.id, -1) AS tagId, IFNULL(t.name, '') AS tagName
        FROM notes n
        LEFT JOIN note_tags nt ON n.id = nt.noteId
        LEFT JOIN tags t ON nt.tagId = t.id
        ORDER BY n.updatedAtMillis DESC, t.name COLLATE NOCASE ASC
        """,
    )
    suspend fun getAllNoteTagRowsForExport(): List<NoteTagJoinRow>
}
