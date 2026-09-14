package com.bdavidgm.notas.data

import android.content.Context
import android.net.Uri
import com.bdavidgm.notas.data.local.NoteEntity
import com.bdavidgm.notas.data.local.NoteImageEntity
import com.bdavidgm.notas.data.local.NoteTagCrossRef
import com.bdavidgm.notas.data.local.NotasDao
import com.bdavidgm.notas.data.local.TagEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.bdavidgm.notas.ui.util.parsePlainNoteDocument

class NotasRepository(
    private val dao: NotasDao,
    private val appContext: Context,
) {

    fun observeNotesMatchingSearch(searchPattern: String): Flow<List<NoteWithTags>> =
        dao.observeNoteTagJoinRowsBySearch(searchPattern).map { it.toNoteWithTagsList() }

    fun observeNoteWithTags(noteId: Long): Flow<NoteWithTags?> =
        dao.observeNoteTagJoinRowsForNote(noteId).map { it.toSingleNoteWithTags() }

    fun observeImages(noteId: Long) = dao.observeImagesForNote(noteId)

    fun observeAllTags(): Flow<List<TagEntity>> = dao.observeAllTags()

    suspend fun createBlankNote(): Long {
        val now = System.currentTimeMillis()
        return dao.insertNote(
            NoteEntity(
                title = "",
                content = "",
                createdAtMillis = now,
                updatedAtMillis = now,
            ),
        )
    }

    /**
     * Lee un .txt/.md desde [uri], crea una nota en la base de datos y devuelve su id.
     */
    suspend fun importNoteFromTextFile(uri: Uri): Long = withContext(Dispatchers.IO) {
        val text = appContext.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(StandardCharsets.UTF_8).readText()
        } ?: throw IOException("No se pudo leer el archivo.")

        val displayName = queryDisplayName(uri)
            ?.substringBeforeLast('.')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        val parsed = parsePlainNoteDocument(text, displayName)
        val now = System.currentTimeMillis()
        val created = parsed.createdAtMillis ?: now
        val updated = parsed.updatedAtMillis ?: now
        val noteId = dao.insertNote(
            NoteEntity(
                title = parsed.title.trimEnd(),
                content = parsed.content,
                createdAtMillis = created,
                updatedAtMillis = updated,
            ),
        )
        for (tagName in parsed.tagNames) {
            addTagToNote(noteId, tagName)
        }
        noteId
    }

    private fun queryDisplayName(uri: Uri): String? {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        appContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment
    }

    suspend fun updateNote(note: NoteEntity) {
        dao.updateNote(note)
    }

    suspend fun getNote(id: Long): NoteEntity? = dao.getNote(id)

    suspend fun persistDraftIfChanged(noteId: Long, title: String, content: String) {
        val current = dao.getNote(noteId) ?: return
        val t = title.trimEnd()
        if (t == current.title && content == current.content) return
        dao.updateNote(
            current.copy(
                title = t,
                content = content,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deleteNote(noteId: Long) {
        val images = dao.observeImagesForNote(noteId).first()
        withContext(Dispatchers.IO) {
            images.forEach { File(it.storedPath).delete() }
            File(appContext.filesDir, "note_images/$noteId").deleteRecursively()
        }
        dao.deleteNoteById(noteId)
    }

    suspend fun addTagToNote(noteId: Long, rawName: String) {
        val name = rawName.trim()
        if (name.isEmpty()) return
        dao.insertTag(TagEntity(name = name))
        val tag = dao.getTagByName(name) ?: return
        dao.linkTagToNote(NoteTagCrossRef(noteId = noteId, tagId = tag.id))
    }

    suspend fun removeTagFromNote(noteId: Long, tagId: Long) {
        dao.unlinkTag(noteId, tagId)
    }

    /** Elimina la etiqueta de todas las notas y del catálogo. */
    suspend fun deleteTag(tagId: Long) {
        dao.deleteTagById(tagId)
    }

    suspend fun copyGalleryImagesToNote(noteId: Long, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val existing = dao.observeImagesForNote(noteId).first()
        var order = existing.maxOfOrNull { it.sortOrder }?.plus(1) ?: 0
        withContext(Dispatchers.IO) {
            val dir = File(appContext.filesDir, "note_images/$noteId").apply { mkdirs() }
            for (uri in uris) {
                val dest = File(dir, "${UUID.randomUUID()}.jpg")
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                } ?: continue
                dao.insertNoteImage(
                    NoteImageEntity(
                        noteId = noteId,
                        storedPath = dest.absolutePath,
                        sortOrder = order++,
                    ),
                )
            }
        }
    }

    suspend fun deleteImage(imageId: Long, storedPath: String) {
        withContext(Dispatchers.IO) {
            File(storedPath).delete()
        }
        dao.deleteImage(imageId)
    }

    /**
     * Escribe el texto de una nota exportada en el URI elegido por el usuario (.txt / .md).
     */
    suspend fun writeTextExport(destinationUri: Uri, text: String) {
        withContext(Dispatchers.IO) {
            val out = appContext.contentResolver.openOutputStream(destinationUri)
                ?: throw IOException("No se pudo escribir en el destino elegido.")
            out.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                writer.write(text)
            }
        }
    }

    /**
     * Exporta todas las notas (texto, etiquetas e imágenes) a un ZIP con [manifest.json].
     */
    suspend fun exportAllNotesToZip(destinationUri: Uri) {
        withContext(Dispatchers.IO) {
            val out = appContext.contentResolver.openOutputStream(destinationUri)
                ?: throw IOException("No se pudo escribir en el destino elegido.")
            BufferedOutputStream(out).use { buffered ->
                exportAllNotesToZipStream(buffered)
            }
        }
    }

    /**
     * Importa notas desde un ZIP generado por esta app. Devuelve el número de notas creadas.
     */
    suspend fun importNotesFromZip(sourceUri: Uri): Int =
        withContext(Dispatchers.IO) {
            val input = appContext.contentResolver.openInputStream(sourceUri)
                ?: throw IOException("No se pudo leer el archivo.")
            BufferedInputStream(input).use { buffered ->
                importNotesFromZipStream(buffered)
            }
        }

    private suspend fun exportAllNotesToZipStream(outputStream: OutputStream) {
        val rows = dao.getAllNoteTagRowsForExport()
        val notesWithTags = rows.toNoteWithTagsList()
        val notesJson = JSONArray()
        val imageWrites = mutableListOf<Pair<String, File>>()

        for (nwt in notesWithTags) {
            val exportId = UUID.randomUUID().toString()
            val noteObj = JSONObject()
            noteObj.put("exportId", exportId)
            noteObj.put("title", nwt.note.title)
            noteObj.put("content", nwt.note.content)
            noteObj.put("createdAtMillis", nwt.note.createdAtMillis)
            noteObj.put("updatedAtMillis", nwt.note.updatedAtMillis)
            val tagsArr = JSONArray()
            nwt.tags.forEach { tagsArr.put(it.name) }
            noteObj.put("tags", tagsArr)

            val imagesJson = JSONArray()
            val images = dao.getImagesForNoteExport(nwt.note.id)
            var fileIndex = 0
            for (img in images) {
                val file = File(img.storedPath)
                if (!file.isFile) continue
                val path = "images/$exportId/$fileIndex.jpg"
                fileIndex++
                imagesJson.put(
                    JSONObject()
                        .put("path", path)
                        .put("sortOrder", img.sortOrder),
                )
                imageWrites.add(path to file)
            }
            noteObj.put("images", imagesJson)
            notesJson.put(noteObj)
        }

        val manifest = JSONObject()
            .put("schemaVersion", 1)
            .put("exportedAtMillis", System.currentTimeMillis())
            .put("notes", notesJson)

        ZipOutputStream(outputStream).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifest.toString(2).toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
            for ((zipPath, file) in imageWrites) {
                zos.putNextEntry(ZipEntry(zipPath))
                file.inputStream().use { input -> input.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    private suspend fun importNotesFromZipStream(inputStream: InputStream): Int {
        val stagingDir = File(appContext.cacheDir, "import_${UUID.randomUUID()}").apply { mkdirs() }
        return try {
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val outFile = resolveZipEntrySafe(stagingDir, entry.name)
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            val manifestFile = File(stagingDir, "manifest.json")
            if (!manifestFile.isFile) {
                throw IOException("El archivo no contiene manifest.json válido.")
            }
            val root = JSONObject(manifestFile.readText(StandardCharsets.UTF_8))
            val version = root.optInt("schemaVersion", 0)
            if (version != 1) {
                throw IOException("Formato no compatible (versión $version).")
            }
            val notesArr = root.optJSONArray("notes")
                ?: throw IOException("El manifiesto no incluye la lista de notas.")
            var imported = 0
            for (i in 0 until notesArr.length()) {
                val n = notesArr.getJSONObject(i)
                val title = n.optString("title", "")
                val content = n.optString("content", "")
                val created = n.optLong("createdAtMillis", System.currentTimeMillis())
                val updated = n.optLong("updatedAtMillis", created)
                val noteId = dao.insertNote(
                    NoteEntity(
                        id = 0,
                        title = title,
                        content = content,
                        createdAtMillis = created,
                        updatedAtMillis = updated,
                    ),
                )
                val tags = n.optJSONArray("tags")
                if (tags != null) {
                    for (t in 0 until tags.length()) {
                        val name = tags.getString(t).trim()
                        if (name.isEmpty()) continue
                        dao.insertTag(TagEntity(name = name))
                        val tag = dao.getTagByName(name) ?: continue
                        dao.linkTagToNote(NoteTagCrossRef(noteId = noteId, tagId = tag.id))
                    }
                }
                val images = n.optJSONArray("images")
                if (images != null) {
                    val pairs = mutableListOf<Pair<Int, String>>()
                    for (j in 0 until images.length()) {
                        val im = images.getJSONObject(j)
                        pairs.add(im.optInt("sortOrder", j) to im.getString("path"))
                    }
                    pairs.sortBy { it.first }
                    var order = 0
                    for ((_, path) in pairs) {
                        val src = resolveZipEntrySafe(stagingDir, path)
                        if (!src.isFile) continue
                        val dir = File(appContext.filesDir, "note_images/$noteId").apply { mkdirs() }
                        val dest = File(dir, "${UUID.randomUUID()}.jpg")
                        src.inputStream().use { inp ->
                            dest.outputStream().use { out -> inp.copyTo(out) }
                        }
                        dao.insertNoteImage(
                            NoteImageEntity(
                                noteId = noteId,
                                storedPath = dest.absolutePath,
                                sortOrder = order++,
                            ),
                        )
                    }
                }
                imported++
            }
            imported
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    /** Evita zip slip: la ruta descomprimida debe quedar bajo [baseDir]. */
    private fun resolveZipEntrySafe(baseDir: File, zipRelativePath: String): File {
        val child = File(baseDir, zipRelativePath)
        val baseCanon = baseDir.canonicalPath + File.separator
        val childCanon = child.canonicalPath
        if (!childCanon.startsWith(baseCanon)) {
            throw SecurityException("Entrada ZIP no permitida: $zipRelativePath")
        }
        return child
    }

    companion object {
        fun likePattern(raw: String): String {
            if (raw.isBlank()) return "%"
            val safe = raw.replace('%', ' ').replace('_', ' ')
            return "%$safe%"
        }
    }
}
