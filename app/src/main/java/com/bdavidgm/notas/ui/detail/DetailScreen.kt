package com.bdavidgm.notas.ui.detail

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.bdavidgm.notas.R
import com.bdavidgm.notas.data.local.NoteEntity
import com.bdavidgm.notas.data.local.NoteImageEntity
import com.bdavidgm.notas.data.local.TagEntity
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
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.OutlinedRichTextEditor
import com.mohamedrejeb.richeditor.ui.material3.RichText
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditorDefaults
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onNavigateBack: () -> Unit,
) {
    val nwt by viewModel.noteWithTags.collectAsStateWithLifecycle()
    val isEditing by viewModel.isEditing.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var overflowOpen by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showCopyDialog by remember { mutableStateOf(false) }
    var showManageTags by remember { mutableStateOf(false) }
    var copyOptions by remember { mutableStateOf(NoteCopyOptions()) }
    var pendingExportFormat by remember { mutableStateOf<NoteExportFormat?>(null) }

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

    BackHandler(enabled = showManageTags) {
        showManageTags = false
    }

    BackHandler(enabled = isEditing && !showManageTags) {
        scope.launch {
            viewModel.saveDraftIfEditing()
            onNavigateBack()
        }
    }

    val titleBar = nwt?.note?.title?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.detail_default_title)

    if (showManageTags) {
        ManageTagsScreen(
            viewModel = viewModel,
            noteTags = nwt?.tags.orEmpty(),
            onDismiss = { showManageTags = false },
        )
        return
    }

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
                                suggestedNoteExportFileName(
                                    viewModel.draftTitle.value,
                                    NoteExportFormat.TXT,
                                ),
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
                                suggestedNoteExportFileName(
                                    viewModel.draftTitle.value,
                                    NoteExportFormat.MD,
                                ),
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
                            title = viewModel.draftTitle.value,
                            createdAtMillis = note?.createdAtMillis ?: System.currentTimeMillis(),
                            updatedAtMillis = note?.updatedAtMillis ?: System.currentTimeMillis(),
                            content = viewModel.draftContent.value,
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
                        text = { Text(stringResource(R.string.action_manage_tags_menu)) },
                        onClick = {
                            overflowOpen = false
                            showManageTags = true
                        },
                    )
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

        DetailNoteBody(
            viewModel = viewModel,
            note = note,
            tags = nwt?.tags.orEmpty(),
            isEditing = isEditing,
            onAddPhotos = {
                galleryLauncher.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                    ),
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

@Composable
private fun DetailNoteBody(
    viewModel: DetailViewModel,
    note: NoteEntity,
    tags: List<TagEntity>,
    isEditing: Boolean,
    onAddPhotos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val removeTag = remember(viewModel) { viewModel::removeTag }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .padding(16.dp)
            // Ignora bring-into-view del TextField: con rich text multi-párrafo el
            // mapeo de offsets empuja el scroll al final del documento al hacer clic.
            .ignoreChildBringIntoView()
            .verticalScroll(scrollState),
    ) {
        Text(
            text = noteTimestampLabel(note.createdAtMillis, note.updatedAtMillis),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )

        Spacer(Modifier.height(12.dp))

        DetailContentModeRow(viewModel)

        Spacer(Modifier.height(12.dp))

        if (isEditing) {
            DraftTitleField(viewModel)
            Spacer(Modifier.height(8.dp))
            DraftContentEditor(viewModel = viewModel)
        } else {
            ReadOnlyNoteBody(viewModel)
        }

        Spacer(Modifier.height(20.dp))
        DetailMetaSections(
            tags = tags,
            isEditing = isEditing,
            onRemoveTag = removeTag,
            viewModel = viewModel,
            onAddPhotos = onAddPhotos,
        )
    }
}

/**
 * Evita que hijos (p. ej. BasicTextField) pidan scroll al padre al enfocar/clic.
 * Sin esto, el editor rich-text suele llevar el viewport al final del texto.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.ignoreChildBringIntoView(): Modifier =
    bringIntoViewResponder(
        object : BringIntoViewResponder {
            override fun calculateRectForParent(localRect: Rect): Rect = localRect
            override suspend fun bringChildIntoView(localRect: () -> Rect?) {
                // no-op
            }
        },
    )

@Composable
private fun DetailMetaSections(
    tags: List<TagEntity>,
    isEditing: Boolean,
    onRemoveTag: (Long) -> Unit,
    viewModel: DetailViewModel,
    onAddPhotos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.section_tags),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(8.dp))
        NoteTagsRow(
            tags = tags,
            isEditing = isEditing,
            onRemoveTag = onRemoveTag,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.section_photos),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(8.dp))
        NotePhotosSection(
            viewModel = viewModel,
            isEditing = isEditing,
            onAddPhotos = onAddPhotos,
        )
    }
}

@Composable
private fun DetailContentModeRow(viewModel: DetailViewModel) {
    val contentMode by viewModel.contentDisplayMode.collectAsStateWithLifecycle()
    ContentModeSelector(
        mode = contentMode,
        onModeChange = viewModel::setContentDisplayMode,
    )
}

@Composable
private fun DraftTitleField(viewModel: DetailViewModel) {
    val draftTitle by viewModel.draftTitle.collectAsStateWithLifecycle()
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
}

@Composable
private fun DraftContentEditor(viewModel: DetailViewModel) {
    // Ojo: aquí NO se colecta draftContent. Si se leyera en composición, cada
    // pulsación recompondría el editor completo (y su toolbar).
    val contentMode by viewModel.contentDisplayMode.collectAsStateWithLifecycle()

    when (contentMode) {
        ContentDisplayMode.TXT -> PlainTextDraftEditor(viewModel)
        ContentDisplayMode.MD -> RichMarkdownDraftEditor(viewModel)
    }
}

@Composable
private fun PlainTextDraftEditor(viewModel: DetailViewModel) {
    var userEdited by remember { mutableStateOf(false) }
    var localField by remember {
        mutableStateOf(TextFieldValue(viewModel.draftContent.value, TextRange.Zero))
    }

    LaunchedEffect(viewModel) {
        viewModel.draftContent.collect { remote ->
            if (userEdited || remote == localField.text) return@collect
            localField = TextFieldValue(remote, TextRange.Zero)
        }
    }

    DisposableEffect(viewModel) {
        viewModel.setBodySnapshotProvider { localField.text }
        onDispose {
            viewModel.updateDraftContent(localField.text)
            viewModel.setBodySnapshotProvider(null)
        }
    }

    OutlinedTextField(
        value = localField,
        onValueChange = { updated ->
            userEdited = true
            localField = updated
            viewModel.updateDraftContent(updated.text)
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp),
        label = { Text(stringResource(R.string.field_body)) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Celeste,
            unfocusedBorderColor = Celeste,
            cursorColor = NegroTexto,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
private fun RichMarkdownDraftEditor(viewModel: DetailViewModel) {
    val richTextState = rememberRichTextState()
    var userEdited by remember { mutableStateOf(false) }
    var lastPushed by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel, richTextState) {
        viewModel.draftContent.collect { remote ->
            if (userEdited || remote == lastPushed) return@collect
            richTextState.setMarkdown(remote)
            richTextState.selection = TextRange.Zero
            lastPushed = richTextState.toMarkdown()
        }
    }

    DisposableEffect(viewModel, richTextState) {
        viewModel.setBodySnapshotProvider { richTextState.toMarkdown() }
        onDispose {
            val markdown = richTextState.toMarkdown()
            viewModel.updateDraftContent(markdown)
            viewModel.setBodySnapshotProvider(null)
        }
    }

    // Clave de rendimiento: observar una señal barata (el AnnotatedString ya
    // construido por el editor) y serializar a Markdown UNA vez pasado el
    // debounce. Observar `toMarkdown()` dentro del snapshotFlow recorría y
    // serializaba el documento entero en cada pulsación y en cada cambio de
    // selección, en el hilo principal.
    LaunchedEffect(viewModel, richTextState) {
        snapshotFlow { richTextState.annotatedString }
            .drop(1)
            .debounce(400)
            .collect {
                // Aún no se ha cargado el contenido: no pisar el borrador.
                if (lastPushed == null) return@collect
                val markdown = richTextState.toMarkdown()
                if (markdown == lastPushed) return@collect
                userEdited = true
                lastPushed = markdown
                viewModel.updateDraftContent(markdown)
            }
    }

    RichMarkdownFormatToolbar(state = richTextState)
    Spacer(Modifier.height(8.dp))
    OutlinedRichTextEditor(
        state = richTextState,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp),
        label = { Text(stringResource(R.string.field_body)) },
        colors = RichTextEditorDefaults.outlinedRichTextEditorColors(
            focusedBorderColor = Celeste,
            unfocusedBorderColor = Celeste,
            cursorColor = NegroTexto,
        ),
    )
}

@Composable
private fun ReadOnlyNoteBody(viewModel: DetailViewModel) {
    val draftTitle by viewModel.draftTitle.collectAsStateWithLifecycle()
    val draftContent by viewModel.draftContent.collectAsStateWithLifecycle()
    val contentMode by viewModel.contentDisplayMode.collectAsStateWithLifecycle()

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
            ReadOnlyRichMarkdown(markdown = draftContent)
        }
    }
}

@Composable
private fun ReadOnlyRichMarkdown(markdown: String) {
    val richTextState = rememberRichTextState()
    LaunchedEffect(markdown) {
        richTextState.setMarkdown(markdown)
    }
    if (markdown.isBlank()) {
        Text(
            text = stringResource(R.string.empty_body_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        )
    } else {
        RichText(
            state = richTextState,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun NoteTagsRow(
    tags: List<TagEntity>,
    isEditing: Boolean,
    onRemoveTag: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { tag ->
            NoteTagChip(
                tag = tag,
                isEditing = isEditing,
                onRemoveTag = onRemoveTag,
            )
        }
    }
}

@Composable
private fun NoteTagChip(
    tag: TagEntity,
    isEditing: Boolean,
    onRemoveTag: (Long) -> Unit,
) {
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
            IconButton(onClick = { onRemoveTag(tag.id) }) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_remove_tag),
                    tint = NegroTexto,
                )
            }
        }
    }
}

@Composable
private fun NotePhotosSection(
    viewModel: DetailViewModel,
    isEditing: Boolean,
    onAddPhotos: () -> Unit,
) {
    val images by viewModel.images.collectAsStateWithLifecycle()
    val onDeleteImage = remember(viewModel) { viewModel::deleteImage }

    if (isEditing) {
        CelesteElevatedButton(onClick = onAddPhotos) {
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
                onDelete = onDeleteImage,
            )
        }
    }
}

@Composable
private fun ManageTagsScreen(
    viewModel: DetailViewModel,
    noteTags: List<TagEntity>,
    onDismiss: () -> Unit,
) {
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var pendingTagNames by remember { mutableStateOf<List<String>>(emptyList()) }
    // null = sin diálogo; id null = solo pendiente de teclado (aún no en BD).
    var tagPendingDelete by remember { mutableStateOf<Pair<Long?, String>?>(null) }

    val onRemoveNoteTag = remember(viewModel) { viewModel::removeTag }
    val onDeleteTag = remember(viewModel) { viewModel::deleteTag }

    fun applyPending() {
        viewModel.addTags(pendingTagNames)
        pendingTagNames = emptyList()
        query = ""
        onDismiss()
    }

    tagPendingDelete?.let { (tagId, tagName) ->
        AlertDialog(
            onDismissRequest = { tagPendingDelete = null },
            title = { Text(stringResource(R.string.dialog_delete_tag_title)) },
            text = {
                Text(stringResource(R.string.dialog_delete_tag_message, tagName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (tagId != null) {
                            onDeleteTag(tagId)
                        }
                        val key = tagName.lowercase(Locale.getDefault())
                        pendingTagNames = pendingTagNames.filterNot {
                            it.lowercase(Locale.getDefault()) == key
                        }
                        tagPendingDelete = null
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { tagPendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    NotasScaffold(
        title = stringResource(R.string.manage_tags_title),
        navigationIcon = {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = NegroTexto,
                )
            }
        },
        actions = {
            TopBarTextButton(
                label = stringResource(R.string.action_apply_tags),
                enabled = pendingTagNames.isNotEmpty(),
                onClick = { applyPending() },
            )
        },
    ) { padding ->
        ManageTagsList(
            noteTags = noteTags,
            allTags = allTags,
            query = query,
            pendingTagNames = pendingTagNames,
            onQueryChange = { query = it },
            onPendingChange = { pendingTagNames = it },
            onRemoveNoteTag = onRemoveNoteTag,
            onRequestDeleteTag = { id, name -> tagPendingDelete = id to name },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun ManageTagsList(
    noteTags: List<TagEntity>,
    allTags: List<TagEntity>,
    query: String,
    pendingTagNames: List<String>,
    onQueryChange: (String) -> Unit,
    onPendingChange: (List<String>) -> Unit,
    onRemoveNoteTag: (Long) -> Unit,
    onRequestDeleteTag: (id: Long?, name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val noteTagIds = remember(noteTags) { noteTags.map { it.id }.toSet() }
    val noteTagNames = remember(noteTags) {
        noteTags.map { it.name.lowercase(Locale.getDefault()) }.toSet()
    }
    val pendingLower = remember(pendingTagNames) {
        pendingTagNames.map { it.lowercase(Locale.getDefault()) }.toSet()
    }
    val trimmedQuery = query.trim()
    // Las pendientes se muestran arriba, en "En esta nota", así que salen de aquí.
    val availableTags = remember(allTags, noteTagIds, pendingLower) {
        allTags.filter { tag ->
            tag.id !in noteTagIds &&
                tag.name.lowercase(Locale.getDefault()) !in pendingLower
        }
    }
    val suggestions = remember(availableTags, trimmedQuery) {
        availableTags.filter { tag ->
            trimmedQuery.isEmpty() ||
                tag.name.lowercase(Locale.getDefault())
                    .contains(trimmedQuery.lowercase(Locale.getDefault()))
        }
    }
    val canSelectTyped = trimmedQuery.isNotEmpty() &&
        trimmedQuery.lowercase(Locale.getDefault()) !in noteTagNames &&
        trimmedQuery.lowercase(Locale.getDefault()) !in pendingLower

    val togglePendingName = remember(pendingTagNames, onPendingChange) {
        { name: String ->
            val key = name.lowercase(Locale.getDefault())
            onPendingChange(
                if (pendingTagNames.any { it.lowercase(Locale.getDefault()) == key }) {
                    pendingTagNames.filterNot { it.lowercase(Locale.getDefault()) == key }
                } else {
                    pendingTagNames + name
                },
            )
        }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        item(key = "input") {
            ManageTagsQueryField(
                query = query,
                onQueryChange = onQueryChange,
                canSelectTyped = canSelectTyped,
                onSelectTyped = {
                    onPendingChange(pendingTagNames + trimmedQuery)
                    onQueryChange("")
                },
            )
        }

        item(key = "on_note_header") {
            Text(
                text = stringResource(R.string.manage_tags_on_note),
                style = MaterialTheme.typography.titleSmall,
                color = NegroTexto,
            )
            Spacer(Modifier.height(8.dp))
            if (noteTags.isEmpty() && pendingTagNames.isEmpty()) {
                Text(
                    text = stringResource(R.string.manage_tags_on_note_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        items(noteTags, key = { "note-${it.id}" }) { tag ->
            ManageNoteTagRow(
                tag = tag,
                onRemoveNoteTag = onRemoveNoteTag,
                onRequestDeleteTag = onRequestDeleteTag,
            )
        }

        // Pendientes de aplicar: ya se ven aquí, pero no se guardan hasta "Aplicar".
        items(
            pendingTagNames,
            key = { "pending-${it.lowercase(Locale.getDefault())}" },
        ) { name ->
            val existingId = allTags.firstOrNull {
                it.name.lowercase(Locale.getDefault()) == name.lowercase(Locale.getDefault())
            }?.id
            ManagePendingTagRow(
                name = name,
                tagId = existingId,
                onRemovePending = togglePendingName,
                onRequestDeleteTag = onRequestDeleteTag,
            )
        }

        item(key = "existing_header") {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.manage_tags_existing),
                style = MaterialTheme.typography.titleSmall,
                color = NegroTexto,
            )
            Spacer(Modifier.height(8.dp))
            when {
                availableTags.isEmpty() && trimmedQuery.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.manage_tags_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                }
                suggestions.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.manage_tags_no_matches),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                }
            }
        }

        if (suggestions.isNotEmpty()) {
            items(suggestions, key = { "avail-${it.id}" }) { tag ->
                ManageAvailableTagRow(
                    tag = tag,
                    onToggle = togglePendingName,
                    onRequestDeleteTag = onRequestDeleteTag,
                )
            }
        }
    }
}

@Composable
private fun ManageTagsQueryField(
    query: String,
    onQueryChange: (String) -> Unit,
    canSelectTyped: Boolean,
    onSelectTyped: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(R.string.field_new_tag)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = { if (canSelectTyped) onSelectTyped() },
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Celeste,
            unfocusedBorderColor = Celeste,
            cursorColor = NegroTexto,
        ),
    )
    Spacer(Modifier.height(8.dp))
    CelesteElevatedButton(
        onClick = onSelectTyped,
        enabled = canSelectTyped,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.action_select_tag))
    }
    Spacer(Modifier.height(20.dp))
}

/** Etiqueta ya asociada: casilla marcada; desmarcarla la quita de la nota. */
@Composable
private fun ManageNoteTagRow(
    tag: TagEntity,
    onRemoveNoteTag: (Long) -> Unit,
    onRequestDeleteTag: (id: Long?, name: String) -> Unit,
) {
    ManageTagCheckRow(
        name = tag.name,
        checked = true,
        onClick = { onRemoveNoteTag(tag.id) },
        onDelete = { onRequestDeleteTag(tag.id, tag.name) },
        contentDescription = stringResource(R.string.cd_remove_tag),
    )
}

/** Etiqueta escrita o elegida que se guardará al pulsar "Aplicar". */
@Composable
private fun ManagePendingTagRow(
    name: String,
    tagId: Long?,
    onRemovePending: (String) -> Unit,
    onRequestDeleteTag: (id: Long?, name: String) -> Unit,
) {
    ManageTagCheckRow(
        name = name,
        checked = true,
        onClick = { onRemovePending(name) },
        onDelete = { onRequestDeleteTag(tagId, name) },
        contentDescription = stringResource(R.string.cd_remove_pending_tag),
    )
}

/** Etiqueta no asociada: marcarla la pasa a pendiente, arriba. */
@Composable
private fun ManageAvailableTagRow(
    tag: TagEntity,
    onToggle: (String) -> Unit,
    onRequestDeleteTag: (id: Long?, name: String) -> Unit,
) {
    ManageTagCheckRow(
        name = tag.name,
        checked = false,
        onClick = { onToggle(tag.name) },
        onDelete = { onRequestDeleteTag(tag.id, tag.name) },
    )
}

@Composable
private fun ManageTagCheckRow(
    name: String,
    checked: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    contentDescription: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClickLabel = contentDescription, onClick = onClick)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
            )
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = NegroTexto,
                modifier = Modifier.padding(start = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.cd_delete_tag),
                tint = NegroTexto,
            )
        }
    }
    HorizontalDivider()
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
private fun PhotoTile(
    image: NoteImageEntity,
    editable: Boolean,
    onDelete: (NoteImageEntity) -> Unit,
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
                onClick = { onDelete(image) },
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
