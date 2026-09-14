package com.bdavidgm.notas.ui.detail

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bdavidgm.notas.data.NotasRepository
import com.bdavidgm.notas.data.NoteWithTags
import com.bdavidgm.notas.ui.util.NoteExportFormat
import com.bdavidgm.notas.ui.util.buildNoteExportDocument
import com.bdavidgm.notas.ui.util.buildNoteExportPackage
import com.bdavidgm.notas.ui.util.hasExportablePhotos
import com.bdavidgm.notas.ui.util.stripInternalNoteLinks
import com.bdavidgm.notas.ui.util.suggestedNoteExportFileName
import com.bdavidgm.notas.ui.util.suggestedNotePhotosFolderName
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import com.bdavidgm.notas.data.NoteLinkCandidate
import kotlinx.coroutines.ExperimentalCoroutinesApi

/** Carpeta de fotos dentro del ZIP de una nota. */
private const val ZIP_PHOTOS_FOLDER = "imagenes"

sealed interface NoteExportFeedback {
    data object Ok : NoteExportFeedback
    data object Fail : NoteExportFeedback
}

sealed interface NotePhotoFeedback {
    data object Error : NotePhotoFeedback
    data object NoCamera : NotePhotoFeedback
}

sealed interface NoteLinkFeedback {
    data object Missing : NoteLinkFeedback
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class DetailViewModel(
    private val noteId: Long,
    private val repository: NotasRepository,
) : ViewModel() {

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    private val _draftTitle = MutableStateFlow("")
    val draftTitle: StateFlow<String> = _draftTitle.asStateFlow()

    private val _draftContent = MutableStateFlow("")
    val draftContent: StateFlow<String> = _draftContent.asStateFlow()

    private val _contentDisplayMode = MutableStateFlow(ContentDisplayMode.MD)
    val contentDisplayMode: StateFlow<ContentDisplayMode> = _contentDisplayMode.asStateFlow()

    private val _exportFeedback = Channel<NoteExportFeedback>(Channel.BUFFERED)
    val exportFeedback = _exportFeedback.receiveAsFlow()

    private val _photoFeedback = Channel<NotePhotoFeedback>(Channel.BUFFERED)
    val photoFeedback = _photoFeedback.receiveAsFlow()

    private val _linkFeedback = Channel<NoteLinkFeedback>(Channel.BUFFERED)
    val linkFeedback = _linkFeedback.receiveAsFlow()

    private val _navigateToNote = Channel<Long>(Channel.BUFFERED)
    val navigateToNote = _navigateToNote.receiveAsFlow()

    private val _linkSearch = MutableStateFlow("")
    val linkSearch: StateFlow<String> = _linkSearch.asStateFlow()

    val linkCandidates: StateFlow<List<NoteLinkCandidate>> = _linkSearch
        .debounce(300)
        .flatMapLatest { query ->
            repository.observeNotesForLink(query, excludeNoteId = noteId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val noteWithTags = repository.observeNoteWithTags(noteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val allTags = repository.observeAllTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val first = repository.observeNoteWithTags(noteId).filterNotNull().first()
            if (first.note.title.isEmpty() && first.note.content.isEmpty()) {
                _isEditing.value = true
            }
            _draftTitle.value = first.note.title
            _draftContent.value = first.note.content
        }
        // Autoguardado fuera de la composición: la UI no vuelve a leer los drafts
        // para programar el debounce en cada pulsación.
        viewModelScope.launch {
            combine(_draftTitle, _draftContent, ::Pair)
                .debounce(750)
                .collectLatest { (title, content) ->
                    if (!_isEditing.value) return@collectLatest
                    repository.persistDraftIfChanged(
                        noteId = noteId,
                        title = title,
                        content = content,
                    )
                }
        }
    }

    fun onLoadedNote(note: NoteWithTags) {
        if (!_isEditing.value) {
            _draftTitle.value = note.note.title
            _draftContent.value = note.note.content
        }
    }

    fun setEditing(value: Boolean) {
        if (!value && _isEditing.value) {
            // Incluye la serialización viva y ya normalizada del rich editor;
            // no dependemos de que haya vencido el debounce de escritura.
            syncDraftContentFromEditor()
            viewModelScope.launch {
                repository.persistDraftIfChanged(
                    noteId = noteId,
                    title = _draftTitle.value,
                    content = _draftContent.value,
                )
                _isEditing.value = false
            }
        } else if (value) {
            _isEditing.value = true
        }
    }

    suspend fun saveDraftIfEditing() {
        if (!_isEditing.value) return
        syncDraftContentFromEditor()
        repository.persistDraftIfChanged(
            noteId = noteId,
            title = _draftTitle.value,
            content = _draftContent.value,
        )
        _isEditing.value = false
    }

    fun updateDraftTitle(v: String) {
        _draftTitle.value = v
    }

    fun updateDraftContent(v: String) {
        _draftContent.value = v
    }

    /**
     * Proveedor del cuerpo “vivo” del editor actual (MD/TXT).
     * Se consulta al cambiar de modo para no perder texto aún no volcado al StateFlow.
     */
    private var bodySnapshotProvider: (() -> String)? = null

    fun setBodySnapshotProvider(provider: (() -> String)?) {
        bodySnapshotProvider = provider
    }

    fun setContentDisplayMode(mode: ContentDisplayMode) {
        if (mode == _contentDisplayMode.value) return
        bodySnapshotProvider?.invoke()?.let { snapshot ->
            _draftContent.value = snapshot
        }
        _contentDisplayMode.value = mode
    }

    fun addTag(rawName: String) {
        val name = rawName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            repository.addTagToNote(noteId, name)
        }
    }

    fun addTags(rawNames: Collection<String>) {
        val names = rawNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (names.isEmpty()) return
        viewModelScope.launch {
            for (name in names) {
                repository.addTagToNote(noteId, name)
            }
        }
    }

    fun removeTag(tagId: Long) {
        viewModelScope.launch {
            repository.removeTagFromNote(noteId, tagId)
        }
    }

    fun deleteTag(tagId: Long) {
        viewModelScope.launch {
            repository.deleteTag(tagId)
        }
    }

    /** Foto elegida en la galería para incrustar en el cuerpo; devuelve su ruta. */
    suspend fun importBodyPhoto(uri: Uri): String? =
        repository.copyImageIntoNoteBody(noteId, uri)

    /** Fichero donde la app de cámara escribirá la captura. */
    suspend fun newBodyPhotoFile(): File =
        repository.newNoteBodyImageFile(noteId)

    /** La captura salió bien: la foto pasa a la tabla de imágenes. */
    suspend fun registerBodyPhoto(file: File) {
        repository.registerNoteBodyImage(noteId, file)
    }

    suspend fun discardBodyPhoto(file: File) {
        repository.deleteBodyImageFile(file)
    }

    /** Foto quitada del cuerpo: fuera de la tabla y del disco. */
    fun removeBodyPhoto(url: String) {
        val path = url.removePrefix("file://")
        viewModelScope.launch { repository.removeNoteBodyImage(noteId, path) }
    }

    fun reportPhotoError() {
        viewModelScope.launch { _photoFeedback.send(NotePhotoFeedback.Error) }
    }

    fun reportCameraMissing() {
        viewModelScope.launch { _photoFeedback.send(NotePhotoFeedback.NoCamera) }
    }

    fun setLinkSearch(query: String) {
        _linkSearch.value = query
    }

    fun clearLinkSearch() {
        _linkSearch.value = ""
    }

    /**
     * Toque en un enlace interno en la vista de lectura: navega a la nota o
     * avisa si ya no existe.
     */
    fun followNoteLink(uid: String) {
        viewModelScope.launch {
            val targetId = repository.getNoteIdByUid(uid)
            if (targetId == null) {
                _linkFeedback.send(NoteLinkFeedback.Missing)
                return@launch
            }
            if (targetId == noteId) return@launch
            _navigateToNote.send(targetId)
        }
    }

    /** ¿La nota lleva fotos que haya que empaquetar al exportar? */
    fun currentBodyHasPhotos(): Boolean {
        syncDraftContentFromEditor()
        return hasExportablePhotos(_draftContent.value)
    }

    /** Solo texto: fotos como url; enlaces internos aplanados al texto visible. */
    fun exportNote(destinationUri: Uri, format: NoteExportFormat) {
        viewModelScope.launch {
            runExport {
                val data = exportData()
                repository.writeTextExport(
                    destinationUri = destinationUri,
                    text = buildNoteExportDocument(
                        title = data.title,
                        createdAtMillis = data.createdAtMillis,
                        updatedAtMillis = data.updatedAtMillis,
                        content = stripInternalNoteLinks(data.content),
                        tagNames = data.tagNames,
                        format = format,
                    ),
                )
            }
        }
    }

    /** Nota y fotos en un único ZIP. */
    fun exportNoteAsZip(destinationUri: Uri, format: NoteExportFormat) {
        viewModelScope.launch {
            runExport {
                val data = exportData()
                val packaged = data.pack(format, ZIP_PHOTOS_FOLDER)
                repository.writeNoteExportZip(
                    destinationUri = destinationUri,
                    documentName = suggestedNoteExportFileName(data.title, format),
                    document = packaged.document,
                    photosFolderName = ZIP_PHOTOS_FOLDER,
                    photos = packaged.photos,
                )
            }
        }
    }

    /** Nota y carpeta de fotos como archivos sueltos en la carpeta elegida. */
    fun exportNoteToFolder(treeUri: Uri, format: NoteExportFormat) {
        viewModelScope.launch {
            runExport {
                val data = exportData()
                val folderName = suggestedNotePhotosFolderName(data.title)
                repository.writeNoteExportFiles(
                    treeUri = treeUri,
                    documentName = suggestedNoteExportFileName(data.title, format),
                    mimeType = format.mimeType,
                    photosFolderName = folderName,
                    photos = data.pack(format, folderName).photos,
                    buildDocument = { realFolderName ->
                        data.pack(format, realFolderName).document
                    },
                )
            }
        }
    }

    private suspend fun runExport(block: suspend () -> Unit) {
        try {
            block()
            _exportFeedback.send(NoteExportFeedback.Ok)
        } catch (e: Exception) {
            // CancellationException no debe tragarse: el scope se cancela al salir.
            if (e is kotlinx.coroutines.CancellationException) throw e
            _exportFeedback.send(NoteExportFeedback.Fail)
        }
    }

    private suspend fun exportData(): ExportData {
        syncDraftContentFromEditor()
        if (_isEditing.value) {
            repository.persistDraftIfChanged(
                noteId = noteId,
                title = _draftTitle.value,
                content = _draftContent.value,
            )
        }
        val nwt = noteWithTags.value
            ?: repository.observeNoteWithTags(noteId).filterNotNull().first()
        return ExportData(
            title = _draftTitle.value,
            createdAtMillis = nwt.note.createdAtMillis,
            updatedAtMillis = nwt.note.updatedAtMillis,
            content = _draftContent.value,
            tagNames = nwt.tags.map { it.name },
        )
    }

    /** El editor vuelca el cuerpo con retardo; exportar necesita el texto de ahora. */
    private fun syncDraftContentFromEditor() {
        bodySnapshotProvider?.invoke()?.let { _draftContent.value = it }
    }

    private data class ExportData(
        val title: String,
        val createdAtMillis: Long,
        val updatedAtMillis: Long,
        val content: String,
        val tagNames: List<String>,
    )

    private fun ExportData.pack(format: NoteExportFormat, photosFolderName: String) =
        buildNoteExportPackage(
            title = title,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis,
            content = content,
            tagNames = tagNames,
            format = format,
            photosFolderName = photosFolderName,
        )

    companion object {
        fun factory(noteId: Long, repository: NotasRepository) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(DetailViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return DetailViewModel(noteId, repository) as T
            }
        }
    }
}
