package com.bdavidgm.notas.ui.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.bdavidgm.notas.R
import com.bdavidgm.notas.ui.theme.Celeste
import com.bdavidgm.notas.ui.theme.NegroTexto
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.launch
import java.io.File

/**
 * `BasicTextField` no admite contenido en línea, así que ningún editor rich-text
 * de Compose (tampoco compose-rich-editor 1.2.0) puede pintar una imagen dentro
 * del campo: la librería solo las dibuja en su vista de lectura. Por eso el
 * cuerpo se parte en bloques —texto editable y fotos como composables reales— y
 * se vuelve a unir en Markdown para guardar.
 */
private sealed interface BodyBlock {
    val id: Long

    data class Text(override val id: Long, val markdown: String) : BodyBlock

    data class Photo(
        override val id: Long,
        val alt: String,
        val url: String,
    ) : BodyBlock {
        fun toMarkdown(): String = "![$alt]($url)"

        /** Coil resuelve mejor el fichero que la URI `file://`. */
        fun imageModel(): Any =
            if (url.startsWith(FILE_SCHEME)) File(url.removePrefix(FILE_SCHEME)) else url
    }
}

private const val FILE_SCHEME = "file://"

private val IMAGE_MARKDOWN = Regex("""!\[([^\]]*)]\(([^)\n]+)\)""")

/**
 * Marcador temporal para partir un bloque justo donde está el cursor: se inserta
 * en el editor, se serializa a Markdown y el punto de corte cae en el mismo sitio
 * que en el texto visible, sin tener que mapear offsets a mano.
 */
private const val SPLIT_MARK = "@@NOTAS_SPLIT@@"

/** Acceso al contenido vivo de un bloque de texto (aún sin volcar al borrador). */
private class BodyTextHandle(
    val snapshot: () -> String,
    val splitAtCursor: () -> Pair<String, String>,
)

private class BodyBlocksState(initialMarkdown: String) {
    val blocks: SnapshotStateList<BodyBlock> = mutableStateListOf()

    private val handles = mutableMapOf<Long, BodyTextHandle>()
    private var lastId = 0L

    init {
        load(initialMarkdown)
    }

    fun load(markdown: String) {
        val parsed = mutableListOf<BodyBlock>()
        var cursor = 0
        IMAGE_MARKDOWN.findAll(markdown).forEach { match ->
            parsed += BodyBlock.Text(newId(), markdown.substring(cursor, match.range.first).trim())
            parsed += BodyBlock.Photo(
                id = newId(),
                alt = match.groupValues[1],
                url = match.groupValues[2],
            )
            cursor = match.range.last + 1
        }
        // Siempre hay un bloque de texto antes de cada foto y otro al final, así
        // que se puede escribir en cualquier hueco.
        parsed += BodyBlock.Text(newId(), markdown.substring(cursor).trim())

        blocks.clear()
        blocks.addAll(parsed)
    }

    fun registerHandle(id: Long, handle: BodyTextHandle?) {
        if (handle == null) handles.remove(id) else handles[id] = handle
    }

    fun currentMarkdown(): String =
        blocks.mapNotNull { block ->
            when (block) {
                is BodyBlock.Text -> liveText(block).ifBlank { null }
                is BodyBlock.Photo -> block.toMarkdown()
            }
        }.joinToString("\n\n")

    fun updateText(id: Long, markdown: String) {
        val index = blocks.indexOfFirst { it.id == id }
        val block = blocks.getOrNull(index) as? BodyBlock.Text ?: return
        if (block.markdown != markdown) blocks[index] = block.copy(markdown = markdown)
    }

    fun remove(id: Long) {
        blocks.removeAll { it.id == id }
        handles.remove(id)
    }

    /**
     * Inserta la foto en el cursor del bloque [focusedTextId] partiéndolo en dos.
     * Devuelve el id del bloque de texto que queda debajo, para darle el foco.
     */
    fun insertPhoto(focusedTextId: Long?, url: String): Long {
        val photo = BodyBlock.Photo(id = newId(), alt = "", url = url)
        val index = blocks.indexOfFirst { it.id == focusedTextId }
        val split = focusedTextId?.let { handles[it] }?.splitAtCursor()

        if (index < 0 || split == null) {
            val tail = BodyBlock.Text(newId(), "")
            blocks += photo
            blocks += tail
            return tail.id
        }

        // Ids nuevos: el bloque partido se recompone desde su Markdown.
        val tail = BodyBlock.Text(newId(), split.second.trim())
        blocks[index] = BodyBlock.Text(newId(), split.first.trim())
        blocks.add(index + 1, photo)
        blocks.add(index + 2, tail)
        return tail.id
    }

    private fun liveText(block: BodyBlock.Text): String =
        (handles[block.id]?.snapshot?.invoke() ?: block.markdown)
            .replace(SPLIT_MARK, "")
            .trim()

    private fun newId(): Long = ++lastId
}

@Composable
internal fun RichMarkdownDraftEditor(viewModel: DetailViewModel) {
    val body = remember(viewModel) { BodyBlocksState(viewModel.draftContent.value) }
    var lastPushed by remember(viewModel) { mutableStateOf(viewModel.draftContent.value) }
    var userEdited by remember(viewModel) { mutableStateOf(false) }
    var focusedTextId by remember { mutableStateOf<Long?>(null) }
    var pendingFocusId by remember { mutableStateOf<Long?>(null) }
    var activeState by remember { mutableStateOf<RichTextState?>(null) }

    val push = {
        userEdited = true
        val markdown = body.currentMarkdown()
        lastPushed = markdown
        viewModel.updateDraftContent(markdown)
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPhotoDialog by remember { mutableStateOf(false) }
    var cameraTarget by remember { mutableStateOf<File?>(null) }

    val insertPhoto: (String) -> Unit = { path ->
        pendingFocusId = body.insertPhoto(focusedTextId, "$FILE_SCHEME$path")
        push()
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val path = viewModel.importBodyPhoto(uri)
                if (path != null) insertPhoto(path) else viewModel.reportPhotoError()
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved ->
        val file = cameraTarget
        cameraTarget = null
        if (file != null) {
            if (saved) insertPhoto(file.absolutePath)
            else scope.launch { viewModel.discardBodyPhoto(file) }
        }
    }

    if (showPhotoDialog) {
        InsertPhotoDialog(
            onGallery = {
                showPhotoDialog = false
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onCamera = {
                showPhotoDialog = false
                scope.launch {
                    val file = viewModel.newBodyPhotoFile()
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                    cameraTarget = file
                    // Sin app de cámara el launcher lanza ActivityNotFoundException.
                    runCatching { cameraLauncher.launch(uri) }.onFailure {
                        cameraTarget = null
                        viewModel.discardBodyPhoto(file)
                        viewModel.reportCameraMissing()
                    }
                }
            },
            onDismiss = { showPhotoDialog = false },
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.draftContent.collect { remote ->
            if (userEdited || remote == lastPushed) return@collect
            body.load(remote)
            lastPushed = remote
        }
    }

    DisposableEffect(viewModel) {
        viewModel.setBodySnapshotProvider { body.currentMarkdown() }
        onDispose {
            viewModel.updateDraftContent(body.currentMarkdown())
            viewModel.setBodySnapshotProvider(null)
        }
    }

    RichMarkdownFormatToolbar(
        state = activeState,
        onInsertPhoto = { showPhotoDialog = true },
    )

    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.field_body),
        style = MaterialTheme.typography.labelMedium,
        color = NegroTexto.copy(alpha = 0.75f),
    )
    Spacer(Modifier.height(4.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Celeste, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val onlyTextBlock = body.blocks.size == 1
        body.blocks.forEachIndexed { index, block ->
            key(block.id) {
                when (block) {
                    is BodyBlock.Text -> BodyTextBlock(
                        block = block,
                        isLast = index == body.blocks.lastIndex,
                        placeholder = if (onlyTextBlock) {
                            stringResource(R.string.markdown_edit_hint)
                        } else {
                            null
                        },
                        requestFocus = pendingFocusId == block.id,
                        onFocusHandled = { pendingFocusId = null },
                        onFocused = { state ->
                            focusedTextId = block.id
                            activeState = state
                        },
                        onRegisterHandle = { handle -> body.registerHandle(block.id, handle) },
                        onFlush = { markdown -> body.updateText(block.id, markdown) },
                        onEdited = { markdown ->
                            body.updateText(block.id, markdown)
                            push()
                        },
                    )

                    is BodyBlock.Photo -> BodyPhotoBlock(
                        photo = block,
                        onRemove = {
                            body.remove(block.id)
                            push()
                        },
                    )
                }
            }
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun BodyTextBlock(
    block: BodyBlock.Text,
    isLast: Boolean,
    placeholder: String?,
    requestFocus: Boolean,
    onFocusHandled: () -> Unit,
    onFocused: (RichTextState) -> Unit,
    onRegisterHandle: (BodyTextHandle?) -> Unit,
    onFlush: (String) -> Unit,
    onEdited: (String) -> Unit,
) {
    // El estado se inicializa aquí (y no en un LaunchedEffect) para que el primer
    // annotatedString ya sea el definitivo: así `drop(1)` descarta la carga y no
    // se confunde con una edición del usuario.
    val state = remember {
        RichTextState().apply {
            setMarkdown(block.markdown)
            selection = TextRange.Zero
        }
    }
    // El round-trip del Markdown normaliza (listas, énfasis…), así que la
    // referencia es lo que el editor devuelve, no lo que se le dio.
    var lastKnown by remember { mutableStateOf(state.toMarkdown()) }
    val interactionSource = remember { IgnorePressInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    val currentOnEdited by rememberUpdatedState(onEdited)
    val currentOnFlush by rememberUpdatedState(onFlush)

    DisposableEffect(state) {
        onRegisterHandle(
            BodyTextHandle(
                snapshot = { state.toMarkdown() },
                splitAtCursor = {
                    state.addTextAfterSelection(SPLIT_MARK)
                    val parts = state.toMarkdown().split(SPLIT_MARK, limit = 2)
                    parts[0] to parts.getOrElse(1) { "" }
                },
            ),
        )
        onDispose {
            currentOnFlush(state.toMarkdown())
            onRegisterHandle(null)
        }
    }

    // Clave de rendimiento: observar el annotatedString (señal barata) y
    // serializar a Markdown una sola vez pasado el debounce.
    LaunchedEffect(state) {
        snapshotFlow { state.annotatedString }
            .drop(1)
            .debounce(400)
            .collect {
                val markdown = state.toMarkdown()
                if (markdown == lastKnown) return@collect
                lastKnown = markdown
                currentOnEdited(markdown)
            }
    }

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            onFocusHandled()
        }
    }

    BasicRichTextEditor(
        state = state,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (isLast) 120.dp else 28.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocused(state) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = NegroTexto),
        interactionSource = interactionSource,
        cursorBrush = SolidColor(NegroTexto),
        decorationBox = { innerTextField ->
            Box {
                if (placeholder != null && state.annotatedString.text.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = NegroTexto.copy(alpha = 0.45f),
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun BodyPhotoBlock(
    photo: BodyBlock.Photo,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    Box(Modifier.fillMaxWidth()) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(photo.imageModel())
                .crossfade(true)
                .build(),
            contentDescription = photo.alt.ifBlank { null },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit,
        )
        IconButton(
            onClick = onRemove,
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

@Composable
private fun InsertPhotoDialog(
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_insert_photo_title)) },
        text = {
            Column {
                TextButton(
                    onClick = onGallery,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_photo_gallery))
                }
                TextButton(
                    onClick = onCamera,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_photo_camera))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * compose-rich-editor (rc10) recoloca la selección en cada `PressInteraction.Press`
 * con una heurística de líneas propia: si el texto tiene líneas plegadas, descarta
 * el offset que calculó `BasicTextField` y el toque acaba en otro sitio o se ignora
 * (issue #304 de la librería). Como su `VisualTransformation` usa
 * `OffsetMapping.Identity`, el mapeo nativo ya es correcto, así que basta con no
 * entregarle el evento de pulsación. El foco y los colores siguen llegando.
 */
private class IgnorePressInteractionSource(
    private val delegate: MutableInteractionSource = MutableInteractionSource(),
) : MutableInteractionSource by delegate {
    override val interactions: Flow<Interaction> =
        delegate.interactions.filterNot { it is PressInteraction.Press }
}
