package com.bdavidgm.notas.ui.util

import java.text.SimpleDateFormat
import java.util.Locale

data class ParsedNoteDocument(
    val title: String,
    val content: String,
    val tagNames: List<String>,
    val createdAtMillis: Long? = null,
    val updatedAtMillis: Long? = null,
)

private val signatureRegex =
    Regex("""^(Creada|Última edición):\s*(.+)$""", RegexOption.IGNORE_CASE)
private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

/**
 * Interpreta un .txt/.md: si coincide con el formato de exportación de la app
 * (título, firma, cuerpo, #etiquetas), lo descompone; si no, usa el nombre
 * del archivo como título y todo el texto como cuerpo.
 */
fun parseImportedNoteDocument(
    rawText: String,
    fallbackTitle: String? = null,
): ParsedNoteDocument {
    val text = rawText.replace("\r\n", "\n").replace('\r', '\n').trim()
    if (text.isEmpty()) {
        return ParsedNoteDocument(
            title = fallbackTitle.orEmpty(),
            content = "",
            tagNames = emptyList(),
        )
    }

    val blocks = text.split(Regex("\n{2,}"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    if (blocks.isEmpty()) {
        return ParsedNoteDocument(
            title = fallbackTitle.orEmpty(),
            content = "",
            tagNames = emptyList(),
        )
    }

    var index = 0
    var title = blocks[index].lineSequence().first().trim().removePrefix("#").trim()
    index++

    var createdAt: Long? = null
    var updatedAt: Long? = null
    if (index < blocks.size) {
        val sigMatch = signatureRegex.matchEntire(blocks[index].lineSequence().first().trim())
        if (sigMatch != null) {
            val kind = sigMatch.groupValues[1]
            val millis = parseExportDate(sigMatch.groupValues[2].trim())
            if (kind.startsWith("Última", ignoreCase = true) ||
                kind.startsWith("Ultima", ignoreCase = true)
            ) {
                updatedAt = millis
                createdAt = millis
            } else {
                createdAt = millis
                updatedAt = millis
            }
            index++
        }
    }

    var tags = emptyList<String>()
    var bodyEnd = blocks.size
    if (index < blocks.size && looksLikeTagsLine(blocks.last())) {
        tags = extractHashtags(blocks.last())
        bodyEnd = blocks.size - 1
    }

    val body = if (index < bodyEnd) {
        blocks.subList(index, bodyEnd).joinToString("\n\n")
    } else {
        ""
    }

    // Si no había estructura clara (una sola línea/bloque sin firma ni tags) y hay fallback,
    // preferir el nombre de archivo solo cuando el "título" parece ser todo el contenido corto.
    val resolvedTitle = title.ifBlank { fallbackTitle.orEmpty() }

    return ParsedNoteDocument(
        title = resolvedTitle,
        content = body,
        tagNames = tags,
        createdAtMillis = createdAt,
        updatedAtMillis = updatedAt,
    )
}

/**
 * Para archivos planos sin formato de exportación: título = nombre de archivo,
 * contenido = texto completo.
 */
fun parsePlainNoteDocument(
    rawText: String,
    fallbackTitle: String?,
): ParsedNoteDocument {
    val structured = parseImportedNoteDocument(rawText, fallbackTitle)
    // Si el parseo estructurado dejó el cuerpo vacío y metió casi todo en el título
    // (archivo de una sola línea/párrafo sin firma), tratarlo como texto plano.
    val trimmed = rawText.replace("\r\n", "\n").replace('\r', '\n').trim()
    val blocks = trimmed.split(Regex("\n{2,}")).map { it.trim() }.filter { it.isNotEmpty() }
    val looksStructured = blocks.size >= 2 && (
        signatureRegex.containsMatchIn(blocks.getOrElse(1) { "" }) ||
            (blocks.size >= 3 && looksLikeTagsLine(blocks.last()))
        )
    return if (looksStructured) {
        structured
    } else {
        ParsedNoteDocument(
            title = fallbackTitle?.takeIf { it.isNotBlank() }.orEmpty(),
            content = trimmed,
            tagNames = emptyList(),
        )
    }
}

private fun parseExportDate(value: String): Long? =
    try {
        dateFormat.parse(value)?.time
    } catch (_: Exception) {
        null
    }

private fun looksLikeTagsLine(block: String): Boolean {
    val line = block.lineSequence().first().trim()
    if (line.isEmpty() || !line.contains('#')) return false
    val tokens = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return false
    return tokens.all { token ->
        token.startsWith("#") && token.length > 1 && !token.contains('\n')
    }
}

private fun extractHashtags(block: String): List<String> =
    block.lineSequence().first().trim()
        .split(Regex("\\s+"))
        .map { it.trim().removePrefix("#").trim() }
        .filter { it.isNotEmpty() }
        .distinct()
