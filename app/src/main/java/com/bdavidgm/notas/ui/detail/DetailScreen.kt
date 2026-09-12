package com.bdavidgm.notas.ui.detail

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.bdavidgm.notas.R
import com.bdavidgm.notas.data.local.NoteImageEntity
import com.bdavidgm.notas.ui.components.CelesteElevatedButton
import com.bdavidgm.notas.ui.components.NotasScaffold
import com.bdavidgm.notas.ui.components.TopBarTextButton
import com.bdavidgm.notas.ui.theme.Celeste
import com.bdavidgm.notas.ui.theme.NegroTexto
import com.bdavidgm.notas.ui.util.NoteCopyOptions
import com.bdavidgm.notas.ui.util.NoteExportFormat
import com.bdavidgm.notas.ui.util.buildNoteCopyText
import com.bdavidgm.notas.ui.util.noteTimestampLabel
import com.bdavidgm.notas.ui.util.suggestedNoteExportFileName
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onNavigateBack: () -> Unit,
) {
    val nwt by viewModel.noteWithTags.collectAsStateWithLifecycle()
    val isEditing by viewModel.isEditing.collectAsStateWithLifecycle()
    val draftTitle by viewModel.draftTitle.collectAsStateWithLifecycle()
    val draftContent by viewModel.draftContent.collectAsStateWithLifecycle()
    val newTagInput by viewModel.newTagInput.collectAsStateWithLifecycle()
    val images by viewModel.images.collectAsStateWithLifecycle()
    val contentMode by viewModel.contentDisplayMode.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var contentField by remember { mutableStateOf(TextFieldValue(draftContent)) }
    var overflowOpen by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showCopyDialog by remember { mutableStateOf(false) }
    var copyOptions by remember { mutableStateOf(NoteCopyOptions()) }
    var pendingExportFormat by remember { mutableStateOf<NoteExportFormat?>(null) }

    LaunchedEffect(draftContent) {
        if (contentField.text != draftContent) {
            contentField = TextFieldValue(
                text = draftContent,
                selection = TextRange(draftContent.length),
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.exportFeedback.collect { feedback ->
            val message = when (feedback) {
                NoteExportFeedback.Ok -> context.getString(R.string.snackbar_note_export_ok)
                NoteExportFeedback.Fail -> context.getString(R.string.snackbar_note_export_error)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    val exportTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(NoteExportFormat.TXT.mimeType),
    ) { uri ->
        val format = pendingExportFormat
        pendingExportFormat = null
        if (uri != null && format == NoteExportFormat.TXT) {
            viewModel.exportNote(uri, NoteExportFormat.TXT)
        }
    }

    val exportMdLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(NoteExportFormat.MD.mimeType),
    ) { uri ->
        val format = pendingExportFormat
        pendingExportFormat = null
        if (uri != null && format == NoteExportFormat.MD) {
            viewModel.exportNote(uri, NoteExportFormat.MD)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            viewModel.addPictures(uris)
        }
    }

    LaunchedEffect(nwt?.note?.updatedAtMillis, isEditing) {
        val note = nwt ?: return@LaunchedEffect
        if (!isEditing) {
            viewModel.onLoadedNote(note)
        }
    }

    LaunchedEffect(draftTitle, draftContent, isEditing) {
        if (!isEditing) return@LaunchedEffect
        delay(750)
        viewModel.persistDraftDebounced()
    }

    BackHandler(enabled = isEditing) {
        scope.launch {
            viewModel.saveDraftIfEditing()
            onNavigateBack()
        }
    }

    val titleBar = nwt?.note?.title?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.detail_default_title)

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(stringResource(R.string.dialog_export_note_title)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showExportDialog = false
                            pendingExportFormat = NoteExportFormat.TXT
                            exportTxtLauncher.launch(
                                suggestedNoteExportFileName(draftTitle, NoteExportFormat.TXT),
                            )
                        },
                    ) {
                        Text(stringResource(R.string.action_export_txt))
                    }
                    TextButton(
                        onClick = {
                            showExportDialog = false
                            pendingExportFormat = NoteExportFormat.MD
                            exportMdLauncher.launch(
                                suggestedNoteExportFileName(draftTitle, NoteExportFormat.MD),
                            )
                        },
                    ) {
                        Text(stringResource(R.string.action_export_md))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showCopyDialog) {
        AlertDialog(
            onDismissRequest = { showCopyDialog = false },
            title = { Text(stringResource(R.string.dialog_copy_note_title)) },
            text = {
                Column {
                    CopyOptionRow(
                        label = stringResource(R.string.copy_option_title),
                        checked = copyOptions.includeTitle,
                        onCheckedChange = { copyOptions = copyOptions.copy(includeTitle = it) },
                    )
                    CopyOptionRow(
                        label = stringResource(R.string.copy_option_body),
                        checked = copyOptions.includeBody,
                        onCheckedChange = { copyOptions = copyOptions.copy(includeBody = it) },
                    )
                    CopyOptionRow(
                        label = stringResource(R.string.copy_option_signature),
                        checked = copyOptions.includeSignature,
                        onCheckedChange = { copyOptions = copyOptions.copy(includeSignature = it) },
                    )
                    CopyOptionRow(
                        label = stringResource(R.string.copy_option_tags),
                        checked = copyOptions.includeTags,
                        onCheckedChange = { copyOptions = copyOptions.copy(includeTags = it) },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!copyOptions.hasAny) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.snackbar_note_copy_empty),
                                )
                            }
                            return@TextButton
                        }
                        val note = nwt?.note
                        val text = buildNoteCopyText(
                            title = draftTitle,
                            createdAtMillis = note?.createdAtMillis ?: System.currentTimeMillis(),
                            updatedAtMillis = note?.updatedAtMillis ?: System.currentTimeMillis(),
                            content = draftContent,
                            tagNames = nwt?.tags.orEmpty().map { it.name },
                            options = copyOptions,
                        )
                        clipboardManager.setText(AnnotatedString(text))
                        showCopyDialog = false
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                context.getString(R.string.snackbar_note_copy_ok),
                            )
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_copy))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCopyDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    NotasScaffold(
        title = titleBar,
        snackbarHostState = snackbarHostState,
        navigationIcon = {
            IconButton(
                onClick = {
                    if (isEditing) {
                        scope.launch {
                            viewModel.saveDraftIfEditing()
                            onNavigateBack()
                        }
                    } else {
                        onNavigateBack()
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = NegroTexto,
                )
            }
        },
        actions = {
            Box {
                IconButton(onClick = { overflowOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.cd_overflow_menu),
                        tint = NegroTexto,
                    )
                }
                DropdownMenu(
                    expanded = overflowOpen,
                    onDismissRequest = { overflowOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_copy_note)) },
                        onClick = {
                            overflowOpen = false
                            copyOptions = NoteCopyOptions()
                            showCopyDialog = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_export_note)) },
                        onClick = {
                            overflowOpen = false
                            showExportDialog = true
                        },
                    )
                }
            }
            if (isEditing) {
                TopBarTextButton(
                    label = stringResource(R.string.action_done),
                    onClick = { viewModel.setEditing(false) },
                )
            } else {
                TopBarTextButton(
                    label = stringResource(R.string.action_edit),
                    onClick = { viewModel.setEditing(true) },
                )
            }
        },
    ) { padding ->
        val note = nwt?.note
        if (note == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.loading))
            }
            return@NotasScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                text = noteTimestampLabel(note.createdAtMillis, note.updatedAtMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )

            Spacer(Modifier.height(12.dp))

            ContentModeSelector(
                mode = contentMode,
                onModeChange = viewModel::setContentDisplayMode,
            )

            Spacer(Modifier.height(12.dp))

            if (isEditing) {
                OutlinedTextField(
                    value = draftTitle,
                    onValueChange = viewModel::updateDraftTitle,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_title)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Celeste,
                        unfocusedBorderColor = Celeste,
                        cursorColor = NegroTexto,
                    ),
                )
                Spacer(Modifier.height(8.dp))

                MarkdownFormatToolbar(
                    value = contentField,
                    onValueChange = { updated ->
                        contentField = updated
                        viewModel.updateDraftContent(updated.text)
                    },
                )
                Spacer(Modifier.height(8.dp))

                when (contentMode) {
                    ContentDisplayMode.TXT -> {
                        OutlinedTextField(
                            value = contentField,
                            onValueChange = { updated ->
                                contentField = updated
                                viewModel.updateDraftContent(updated.text)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            label = { Text(stringResource(R.string.field_body)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Celeste,
                                unfocusedBorderColor = Celeste,
                                cursorColor = NegroTexto,
                            ),
                        )
                    }
                    ContentDisplayMode.MD -> {
                        NoteMarkdownBody(
                            markdown = contentField.text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp)
                                .border(1.dp, Celeste, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = contentField,
                            onValueChange = { updated ->
                                contentField = updated
                                viewModel.updateDraftContent(updated.text)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            label = { Text(stringResource(R.string.markdown_edit_hint)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Celeste,
                                unfocusedBorderColor = Celeste,
                                cursorColor = NegroTexto,
                            ),
                        )
                    }
                }
            } else {
                Text(
                    text = draftTitle.ifBlank { stringResource(R.string.untitled_note) },
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(8.dp))
                when (contentMode) {
                    ContentDisplayMode.TXT -> {
                        Text(
                            text = draftContent.ifBlank { stringResource(R.string.empty_body_hint) },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    ContentDisplayMode.MD -> {
                        NoteMarkdownBody(
                            markdown = draftContent,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.section_tags),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))

            if (isEditing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newTagInput,
                        onValueChange = viewModel::updateNewTagInput,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text(stringResource(R.string.field_new_tag)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Celeste,
                            unfocusedBorderColor = Celeste,
                            cursorColor = NegroTexto,
                        ),
                    )
                    CelesteElevatedButton(onClick = viewModel::addTagFromInput) {
                        Text(stringResource(R.string.action_add_tag))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            val tags = nwt?.tags.orEmpty()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tags.forEach { tag ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Celeste,
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Text(
                                text = tag.name,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = NegroTexto,
                            )
                        }
                        if (isEditing) {
                            IconButton(onClick = { viewModel.removeTag(tag.id) }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.cd_remove_tag),
                                    tint = NegroTexto,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.section_photos),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))

            if (isEditing) {
                CelesteElevatedButton(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                ) {
                    Text(stringResource(R.string.action_add_photos))
                }
                Spacer(Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                images.forEach { img ->
                    PhotoTile(
                        image = img,
                        editable = isEditing,
                        onDelete = { viewModel.deleteImage(img) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CopyOptionRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        )
    }
}

@Composable
private fun NoteMarkdownBody(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    if (markdown.isBlank()) {
        Text(
            text = stringResource(R.string.empty_body_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            modifier = modifier,
        )
    } else {
        Markdown(
            content = markdown,
            modifier = modifier,
        )
    }
}

@Composable
private fun PhotoTile(
    image: NoteImageEntity,
    editable: Boolean,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    Box {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(File(image.storedPath))
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.size(140.dp),
            contentScale = ContentScale.Crop,
        )
        if (editable) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_remove_photo),
                    tint = NegroTexto,
                )
            }
        }
    }
}
