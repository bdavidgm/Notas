package com.bdavidgm.notas.data.local

import androidx.room.ColumnInfo

/**
 * Fila de la lista principal: la búsqueda sigue mirando el cuerpo en SQL,
 * pero no lo trae a memoria. Cargar el Markdown de todas las notas en cada
 * card hacía que el scroll tuviera que arrastrar textos enormes.
 */
data class NoteSummaryJoinRow(
    @ColumnInfo(name = "noteId")
    val noteId: Long,
    val title: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    @ColumnInfo(name = "tagId")
    val tagId: Long,
    @ColumnInfo(name = "tagName")
    val tagName: String,
)
