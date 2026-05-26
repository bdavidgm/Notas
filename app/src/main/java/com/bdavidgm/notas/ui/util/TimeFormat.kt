package com.bdavidgm.notas.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

fun formatNoteInstant(millis: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}

fun noteTimestampLabel(createdAtMillis: Long, updatedAtMillis: Long): String {
    val edited = abs(updatedAtMillis - createdAtMillis) > 1_500L
    return if (edited) {
        "Última edición: ${formatNoteInstant(updatedAtMillis)}"
    } else {
        "Creada: ${formatNoteInstant(createdAtMillis)}"
    }
}
