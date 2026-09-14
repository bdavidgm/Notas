package com.bdavidgm.notas.data.local

import androidx.room.ColumnInfo

/**
 * Fila plana de JOIN nota–etiquetas (sin @Relation) para compatibilidad con KSP + AGP 9.
 */
data class NoteTagJoinRow(
    @ColumnInfo(name = "noteId")
    val noteId: Long,
    val uid: String,
    val title: String,
    val content: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    @ColumnInfo(name = "tagId")
    val tagId: Long,
    @ColumnInfo(name = "tagName")
    val tagName: String,
)
