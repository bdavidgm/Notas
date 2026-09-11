package com.bdavidgm.notas.ui.detail

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

enum class ContentDisplayMode {
    TXT,
    MD,
}

/** Envuelve la selección (o inserta un marcador) con prefijo/sufijo Markdown. */
fun TextFieldValue.wrapSelection(
    prefix: String,
    suffix: String = prefix,
    placeholder: String = "",
): TextFieldValue {
    val start = selection.min
    val end = selection.max
    val selected = text.substring(start, end)
    val inner = selected.ifEmpty { placeholder }
    val insertion = "$prefix$inner$suffix"
    val newText = text.replaceRange(start, end, insertion)
    val innerStart = start + prefix.length
    val newSelection = if (selected.isEmpty() && placeholder.isNotEmpty()) {
        TextRange(innerStart, innerStart + placeholder.length)
    } else {
        TextRange(innerStart + inner.length + suffix.length)
    }
    return copy(text = newText, selection = newSelection)
}

/** Aplica un prefijo al inicio de cada línea cubierta por la selección. */
fun TextFieldValue.applyLinePrefix(prefix: String): TextFieldValue {
    val start = selection.min
    val end = selection.max
    val lineStart = text.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let {
        if (it < 0) 0 else it + 1
    }
    val lineEndExclusive = text.indexOf('\n', end).let {
        if (it < 0) text.length else it
    }
    val block = text.substring(lineStart, lineEndExclusive)
    val rewritten = block.lines().joinToString("\n") { line ->
        if (line.startsWith(prefix)) line else "$prefix$line"
    }
    val newText = text.replaceRange(lineStart, lineEndExclusive, rewritten)
    val delta = rewritten.length - block.length
    return copy(
        text = newText,
        selection = TextRange(
            (start + if (start == lineStart) prefix.length else 0).coerceAtMost(newText.length),
            (end + delta).coerceAtMost(newText.length),
        ),
    )
}

fun TextFieldValue.insertLink(linkText: String, url: String): TextFieldValue {
    val start = selection.min
    val end = selection.max
    val label = linkText.ifBlank {
        text.substring(start, end).ifBlank { "enlace" }
    }
    val href = url.ifBlank { "https://" }
    val insertion = "[$label]($href)"
    val newText = text.replaceRange(start, end, insertion)
    return copy(
        text = newText,
        selection = TextRange(start + insertion.length),
    )
}
