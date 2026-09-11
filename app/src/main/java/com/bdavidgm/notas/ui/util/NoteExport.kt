package com.bdavidgm.notas.ui.util

enum class NoteExportFormat(val extension: String, val mimeType: String) {
    TXT(extension = "txt", mimeType = "text/plain"),
    MD(extension = "md", mimeType = "text/markdown"),
}

fun buildNoteExportDocument(
    title: String,
    createdAtMillis: Long,
    updatedAtMillis: Long,
    content: String,
    tagNames: List<String>,
    format: NoteExportFormat,
): String {
    val displayTitle = title.ifBlank { "Sin título" }
    val titleLine = when (format) {
        NoteExportFormat.MD -> "# $displayTitle"
        NoteExportFormat.TXT -> displayTitle
    }
    val dateLine = noteTimestampLabel(createdAtMillis, updatedAtMillis)
    val tagsLine = tagNames
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ") { tag ->
            if (tag.startsWith("#")) tag else "#$tag"
        }

    return buildString {
        appendLine(titleLine)
        appendLine()
        appendLine(dateLine)
        appendLine()
        append(content.trimEnd())
        if (tagsLine.isNotEmpty()) {
            appendLine()
            appendLine()
            append(tagsLine)
        }
        appendLine()
    }
}

fun suggestedNoteExportFileName(title: String, format: NoteExportFormat): String {
    val base = title
        .ifBlank { "nota" }
        .replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_")
        .trim()
        .take(60)
        .ifBlank { "nota" }
    return "$base.${format.extension}"
}
