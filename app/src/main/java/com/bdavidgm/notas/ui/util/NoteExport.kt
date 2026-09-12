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

data class NoteCopyOptions(
    val includeTitle: Boolean = true,
    val includeBody: Boolean = true,
    val includeSignature: Boolean = true,
    val includeTags: Boolean = true,
) {
    val hasAny: Boolean
        get() = includeTitle || includeBody || includeSignature || includeTags
}

fun buildNoteCopyText(
    title: String,
    createdAtMillis: Long,
    updatedAtMillis: Long,
    content: String,
    tagNames: List<String>,
    options: NoteCopyOptions,
): String {
    if (!options.hasAny) return ""

    val parts = mutableListOf<String>()
    if (options.includeTitle) {
        parts += title.ifBlank { "Sin título" }
    }
    if (options.includeSignature) {
        parts += noteTimestampLabel(createdAtMillis, updatedAtMillis)
    }
    if (options.includeBody) {
        parts += content.trimEnd()
    }
    if (options.includeTags) {
        val tagsLine = tagNames
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ") { tag ->
                if (tag.startsWith("#")) tag else "#$tag"
            }
        if (tagsLine.isNotEmpty()) {
            parts += tagsLine
        }
    }
    return parts.joinToString("\n\n").trim() + "\n"
}
