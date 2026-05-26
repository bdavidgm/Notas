package com.bdavidgm.notas.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdavidgm.notas.R
import com.bdavidgm.notas.data.NoteWithTags
import com.bdavidgm.notas.ui.components.CelesteElevatedButton
import com.bdavidgm.notas.ui.components.CelesteFab
import com.bdavidgm.notas.ui.components.NotasScaffold
import com.bdavidgm.notas.ui.theme.Celeste
import com.bdavidgm.notas.ui.theme.NegroTexto
import com.bdavidgm.notas.ui.util.noteTimestampLabel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenNote: (Long) -> Unit,
) {
    val search = viewModel.searchQuery.collectAsStateWithLifecycle().value
    val notes = viewModel.displayedNotes.collectAsStateWithLifecycle().value
    val tagChips = viewModel.tagChips.collectAsStateWithLifecycle().value

    NotasScaffold(
        title = stringResource(R.string.app_name),
        floatingActionButton = {
            CelesteFab(onClick = { viewModel.createNote(onOpenNote) }) {
                Text(
                    text = stringResource(R.string.fab_new_note),
                    style = MaterialTheme.typography.titleLarge,
                    color = NegroTexto,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = viewModel::onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Celeste,
                    unfocusedBorderColor = Celeste,
                    cursorColor = NegroTexto,
                    focusedLabelColor = NegroTexto,
                ),
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tagChips, key = { it.tagId }) { chip ->
                    CelesteElevatedButton(
                        onClick = { viewModel.toggleTagFilter(chip.tagId) },
                        modifier = Modifier
                            .then(
                                if (chip.selected) {
                                    Modifier.border(2.dp, NegroTexto, RoundedCornerShape(12.dp))
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        val label = stringResource(R.string.tag_chip_label, chip.name, chip.count)
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(notes, key = { it.note.id }) { item ->
                    NoteListItem(
                        item = item,
                        onClick = { onOpenNote(item.note.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteListItem(
    item: NoteWithTags,
    onClick: () -> Unit,
) {
    val titleText = item.note.title.ifBlank { stringResource(R.string.untitled_note) }
    val time = noteTimestampLabel(item.note.createdAtMillis, item.note.updatedAtMillis)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = time,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
