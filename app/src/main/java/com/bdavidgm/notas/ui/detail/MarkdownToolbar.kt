package com.bdavidgm.notas.ui.detail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.bdavidgm.notas.R
import com.bdavidgm.notas.ui.theme.Celeste
import com.bdavidgm.notas.ui.theme.NegroTexto

@Composable
fun ContentModeSelector(
    mode: ContentDisplayMode,
    onModeChange: (ContentDisplayMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = mode == ContentDisplayMode.TXT,
            onClick = { onModeChange(ContentDisplayMode.TXT) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            label = { Text(stringResource(R.string.mode_txt)) },
        )
        SegmentedButton(
            selected = mode == ContentDisplayMode.MD,
            onClick = { onModeChange(ContentDisplayMode.MD) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            label = { Text(stringResource(R.string.mode_md)) },
        )
    }
}

@Composable
fun MarkdownFormatToolbar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLinkDialog by remember { mutableStateOf(false) }
    val placeholderBold = stringResource(R.string.markdown_placeholder_bold)
    val placeholderItalic = stringResource(R.string.markdown_placeholder_italic)
    val placeholderStrike = stringResource(R.string.markdown_placeholder_strike)
    val placeholderCode = stringResource(R.string.markdown_placeholder_code)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Celeste.copy(alpha = 0.35f),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.Start,
        ) {
            FormatIconButton(
                imageVector = Icons.Filled.FormatBold,
                contentDescription = stringResource(R.string.cd_format_bold),
                onClick = {
                    onValueChange(value.wrapSelection("**", placeholder = placeholderBold))
                },
            )
            FormatIconButton(
                imageVector = Icons.Filled.FormatItalic,
                contentDescription = stringResource(R.string.cd_format_italic),
                onClick = {
                    onValueChange(value.wrapSelection("*", placeholder = placeholderItalic))
                },
            )
            FormatIconButton(
                imageVector = Icons.Filled.FormatStrikethrough,
                contentDescription = stringResource(R.string.cd_format_strikethrough),
                onClick = {
                    onValueChange(value.wrapSelection("~~", placeholder = placeholderStrike))
                },
            )
            FormatIconButton(
                imageVector = Icons.Filled.FormatSize,
                contentDescription = stringResource(R.string.cd_format_heading),
                onClick = { onValueChange(value.applyLinePrefix("## ")) },
            )
            FormatIconButton(
                imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                contentDescription = stringResource(R.string.cd_format_bullet),
                onClick = { onValueChange(value.applyLinePrefix("- ")) },
            )
            FormatIconButton(
                imageVector = Icons.Filled.FormatListNumbered,
                contentDescription = stringResource(R.string.cd_format_numbered),
                onClick = { onValueChange(value.applyLinePrefix("1. ")) },
            )
            FormatIconButton(
                imageVector = Icons.Filled.FormatQuote,
                contentDescription = stringResource(R.string.cd_format_quote),
                onClick = { onValueChange(value.applyLinePrefix("> ")) },
            )
            FormatIconButton(
                imageVector = Icons.Filled.Code,
                contentDescription = stringResource(R.string.cd_format_code),
                onClick = {
                    onValueChange(value.wrapSelection("`", placeholder = placeholderCode))
                },
            )
            FormatIconButton(
                imageVector = Icons.Filled.Link,
                contentDescription = stringResource(R.string.cd_format_link),
                onClick = { showLinkDialog = true },
            )
        }
    }

    if (showLinkDialog) {
        LinkInsertDialog(
            initialText = value.text.substring(value.selection.min, value.selection.max),
            onDismiss = { showLinkDialog = false },
            onConfirm = { linkText, url ->
                onValueChange(value.insertLink(linkText, url))
                showLinkDialog = false
            },
        )
    }
}

@Composable
private fun FormatIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = NegroTexto,
        )
    }
}

@Composable
private fun LinkInsertDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (text: String, url: String) -> Unit,
) {
    var linkText by remember(initialText) { mutableStateOf(initialText) }
    var url by remember { mutableStateOf("https://") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_link_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = linkText,
                    onValueChange = { linkText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.field_link_text)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Celeste,
                        unfocusedBorderColor = Celeste,
                        cursorColor = NegroTexto,
                    ),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.field_link_url)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Celeste,
                        unfocusedBorderColor = Celeste,
                        cursorColor = NegroTexto,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(linkText, url) }) {
                Text(stringResource(R.string.action_insert))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
