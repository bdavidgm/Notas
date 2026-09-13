package com.bdavidgm.notas.ui.detail

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bdavidgm.notas.data.NotasRepository
import com.bdavidgm.notas.data.NoteWithTags
import com.bdavidgm.notas.data.local.NoteImageEntity
import com.bdavidgm.notas.ui.util.NoteExportFormat
import com.bdavidgm.notas.ui.util.buildNoteExportDocument
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface NoteExportFeedback {
    data object Ok : NoteExportFeedback
    data object Fail : NoteExportFeedback
}

@OptIn(FlowPreview::class)
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

    val noteWithTags = repository.observeNoteWithTags(noteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val images = repository.observeImages(noteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    suspend fun addPictures(uris: List<android.net.Uri>) {
        repository.copyGalleryImagesToNote(noteId, uris)
    }

    fun deleteImage(entity: NoteImageEntity) {
        viewModelScope.launch {
            repository.deleteImage(entity.id, entity.storedPath)
        }
    }

    fun exportNote(destinationUri: Uri, format: NoteExportFormat) {
        viewModelScope.launch {
            try {
                if (_isEditing.value) {
                    repository.persistDraftIfChanged(
                        noteId = noteId,
                        title = _draftTitle.value,
                        content = _draftContent.value,
                    )
                }
                val nwt = noteWithTags.value
                    ?: repository.observeNoteWithTags(noteId).filterNotNull().first()
                val text = buildNoteExportDocument(
                    title = _draftTitle.value,
                    createdAtMillis = nwt.note.createdAtMillis,
                    updatedAtMillis = nwt.note.updatedAtMillis,
                    content = _draftContent.value,
                    tagNames = nwt.tags.map { it.name },
                    format = format,
                )
                repository.writeTextExport(destinationUri, text)
                _exportFeedback.send(NoteExportFeedback.Ok)
            } catch (_: Exception) {
                _exportFeedback.send(NoteExportFeedback.Fail)
            }
        }
    }

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
