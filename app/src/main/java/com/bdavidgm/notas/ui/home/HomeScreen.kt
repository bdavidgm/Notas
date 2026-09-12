package com.bdavidgm.notas.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdavidgm.notas.R
import com.bdavidgm.notas.data.NoteWithTags
import com.bdavidgm.notas.ui.components.CelesteFab
import com.bdavidgm.notas.ui.components.NotasScaffold
import com.bdavidgm.notas.ui.theme.Celeste
import com.bdavidgm.notas.ui.theme.CelesteClaro
import com.bdavidgm.notas.ui.theme.CelesteOscuro
import com.bdavidgm.notas.ui.theme.NegroTexto
import com.bdavidgm.notas.ui.util.noteTimestampLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenNote: (Long) -> Unit,
    onOpenDrawer: () -> Unit,
) {
    val context = LocalContext.current
    val search = viewModel.searchQuery.collectAsStateWithLifecycle().value
    val notes = viewModel.displayedNotes.collectAsStateWithLifecycle().value
    val tagChips = viewModel.tagChips.collectAsStateWithLifecycle().value

    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) viewModel.performExport(uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.performImport(uri)
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.openDocumentAsNote(uri) { noteId ->
                onOpenNote(noteId)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.backupFeedback.collect { feedback ->
            val msg = when (feedback) {
                BackupFeedback.ExportOk -> context.getString(R.string.snackbar_export_ok)
                BackupFeedback.ExportFail -> context.getString(R.string.snackbar_export_error)
                is BackupFeedback.ImportOk -> context.getString(
                    R.string.snackbar_import_ok,
                    feedback.noteCount,
                )
                BackupFeedback.ImportFail -> context.getString(R.string.snackbar_import_error)
                is BackupFeedback.OpenDocumentOk ->
                    context.getString(R.string.snackbar_open_document_ok)
                BackupFeedback.OpenDocumentFail ->
                    context.getString(R.string.snackbar_open_document_error)
            }
            snackbarHostState.showSnackbar(msg)
        }
    }

    var overflowOpen by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Pair<Long, String>?>(null) }

    pendingDelete?.let { (noteId, titleForDialog) ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.dialog_delete_note_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.dialog_delete_note_message,
                        titleForDialog,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteNote(noteId)
                        pendingDelete = null
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    NotasScaffold(
        title = stringResource(R.string.app_name),
        snackbarHostState = snackbarHostState,
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = stringResource(R.string.cd_open_drawer),
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
                        text = { Text(stringResource(R.string.action_open_document)) },
                        onClick = {
                            overflowOpen = false
                            openDocumentLauncher.launch(
                                arrayOf(
                                    "text/plain",
                                    "text/markdown",
                                    "text/x-markdown",
                                ),
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_export)) },
                        onClick = {
                            overflowOpen = false
                            val fmt = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
                            exportLauncher.launch("notas_${fmt.format(Date())}.zip")
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_import)) },
                        onClick = {
                            overflowOpen = false
                            importLauncher.launch(
                                arrayOf(
                                    "application/zip",
                                    "application/x-zip-compressed",
                                ),
                            )
                        },
                    )
                }
            }
        },
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
                    ElevatedButton(
                        onClick = { viewModel.toggleTagFilter(chip.tagId) },
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = if (chip.selected) CelesteOscuro else CelesteClaro,
                            contentColor = NegroTexto,
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
                        onRequestDelete = { noteId, titleForDialog ->
                            pendingDelete = noteId to titleForDialog
                        },
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
    onRequestDelete: (noteId: Long, titleForDialog: String) -> Unit,
) {
    val titleText = item.note.title.ifBlank { stringResource(R.string.untitled_note) }
    val time = noteTimestampLabel(item.note.createdAtMillis, item.note.updatedAtMillis)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            ) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item.tags.forEach { tag ->
                            Surface(
                                color = Celeste,
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Text(
                                    text = tag.name,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NegroTexto,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            IconButton(
                onClick = {
                    onRequestDelete(item.note.id, titleText)
                },
                modifier = Modifier.widthIn(min = 48.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.cd_delete_note),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
