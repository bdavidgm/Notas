package com.bdavidgm.notas.data.local

/**
 * Fila ligera del diálogo “Enlazar nota”. Room la mapea desde el SELECT de
 * [NotasDao.observeNotesForLink]; el repositorio la convierte a [com.bdavidgm.notas.data.NoteLinkCandidate].
 */
data class NoteLinkCandidateRow(
    val id: Long,
    val uid: String,
    val title: String,
)
