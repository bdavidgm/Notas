package com.bdavidgm.notas.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bdavidgm.notas.data.NotasRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class HomeViewModel(
    private val repository: NotasRepository,
) : ViewModel() {

    private val _search = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _search.asStateFlow()

    private val _selectedTagIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedTagIds: StateFlow<Set<Long>> = _selectedTagIds.asStateFlow()

    private val notesMatchingSearch = _search
        .map { NotasRepository.likePattern(it) }
        .distinctUntilChanged()
        .flatMapLatest { repository.observeNotesMatchingSearch(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val displayedNotes = combine(notesMatchingSearch, _selectedTagIds) { notes, selected ->
        if (selected.isEmpty()) notes
        else notes.filter { n -> selected.all { tid -> n.tags.any { it.id == tid } } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    data class TagChipUi(
        val tagId: Long,
        val name: String,
        val count: Int,
        val selected: Boolean,
    )

    val tagChips = combine(notesMatchingSearch, _selectedTagIds) { notes, selected ->
        val perTag = linkedMapOf<Long, Pair<String, MutableSet<Long>>>()
        for (n in notes) {
            for (t in n.tags) {
                val entry = perTag.getOrPut(t.id) { t.name to mutableSetOf() }
                entry.second.add(n.note.id)
            }
        }
        perTag.map { (id, pair) ->
            TagChipUi(
                tagId = id,
                name = pair.first,
                count = pair.second.size,
                selected = id in selected,
            )
        }.sortedBy { it.name.lowercase(Locale.getDefault()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onSearchChange(value: String) {
        _search.value = value
    }

    fun toggleTagFilter(tagId: Long) {
        val cur = _selectedTagIds.value.toMutableSet()
        if (tagId in cur) cur.remove(tagId) else cur.add(tagId)
        _selectedTagIds.value = cur
    }

    fun createNote(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.createBlankNote()
            onCreated(id)
        }
    }

    companion object {
        fun factory(repository: NotasRepository) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(HomeViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(repository) as T
            }
        }
    }
}
