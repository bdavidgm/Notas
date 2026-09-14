package com.bdavidgm.notas.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.bdavidgm.notas.data.local.NoteEntity
import com.bdavidgm.notas.data.local.NoteImageEntity
import com.bdavidgm.notas.data.local.NoteLinkCrossRef
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
import com.bdavidgm.notas.ui.util.NoteExportPhoto
import com.bdavidgm.notas.ui.util.ensurePureMarkdown
import com.bdavidgm.notas.ui.util.extractNoteLinkUids
import com.bdavidgm.notas.ui.util.parsePlainNoteDocument
import com.bdavidgm.notas.ui.util.relocateBodyPhotos
import com.bdavidgm.notas.ui.util.remapNoteLinkUids

class NotasRepository(
    private val dao: NotasDao,
    private val appContext: Context,
) {

    fun observeNotesMatchingSearch(searchPattern: String): Flow<List<NoteWithTags>> =
        dao.observeNoteTagJoinRowsBySearch(searchPattern).map { it.toNoteWithTagsList() }

    fun observeNoteWithTags(noteId: Long): Flow<NoteWithTags?> =
        dao.observeNoteTagJoinRowsForNote(noteId).map { it.toSingleNoteWithTags() }

    fun observeAllTags(): Flow<List<TagEntity>> = dao.observeAllTags()

    /** Notas candidatas para el diálogo de enlace interno (excluye [excludeNoteId]). */
    fun observeNotesForLink(query: String, excludeNoteId: Long): Flow<List<NoteLinkCandidate>> =
        dao.observeNotesForLink(
            searchPattern = likePattern(query),
            excludeNoteId = excludeNoteId,
        ).map { rows ->
            rows.map { NoteLinkCandidate(id = it.id, uid = it.uid, title = it.title) }
        }

    suspend fun getNoteIdByUid(uid: String): Long? = dao.getNoteIdByUid(uid)

    suspend fun createBlankNote(): Long {
        val now = System.currentTimeMillis()
        return dao.insertNote(
            NoteEntity(
                uid = newNoteUid(),
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
                uid = newNoteUid(),
                title = parsed.title.trimEnd(),
                content = parsed.content,
                createdAtMillis = created,
                updatedAtMillis = updated,
            ),
        )
        for (tagName in parsed.tagNames) {
            addTagToNote(noteId, tagName)
        }
        syncNoteLinks(noteId, parsed.content)
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
        syncNoteLinks(noteId, content)
    }

    /** Reescribe la tabla note_links a partir de los `notas://` del cuerpo. */
    private suspend fun syncNoteLinks(noteId: Long, content: String) {
        dao.deleteNoteLinksForSource(noteId)
        for (uid in extractNoteLinkUids(content)) {
            dao.insertNoteLink(NoteLinkCrossRef(sourceNoteId = noteId, targetUid = uid))
        }
    }

    private fun newNoteUid(): String = UUID.randomUUID().toString()

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

    /**
     * Copia una imagen de la galería al almacenamiento interno de la nota, la
     * registra en note_images y devuelve su ruta absoluta, que es lo que enlaza
     * el cuerpo.
     */
    suspend fun copyImageIntoNoteBody(noteId: Long, uri: Uri): String? {
        val dest = newNoteBodyImageFile(noteId)
        val copied = withContext(Dispatchers.IO) {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false
        }

        if (!copied) {
            withContext(Dispatchers.IO) { dest.delete() }
            return null
        }

        registerNoteBodyImage(noteId, dest)
        return dest.absolutePath
    }

    /** Fichero destino para una captura de cámara, dentro de la carpeta de la nota. */
    suspend fun newNoteBodyImageFile(noteId: Long): File =
        withContext(Dispatchers.IO) {
            val dir = File(appContext.filesDir, "note_images/$noteId").apply { mkdirs() }
            File(dir, "${UUID.randomUUID()}.jpg")
        }

    /**
     * Da de alta en note_images una foto ya escrita en disco (la cámara escribe
     * el fichero por su cuenta, así que se registra cuando la captura ha ido bien).
     */
    suspend fun registerNoteBodyImage(noteId: Long, file: File) {
        val existing = dao.getImagesForNoteExport(noteId)
        if (existing.any { it.storedPath == file.absolutePath }) return
        dao.insertNoteImage(
            NoteImageEntity(
                noteId = noteId,
                storedPath = file.absolutePath,
                sortOrder = existing.maxOfOrNull { it.sortOrder }?.plus(1) ?: 0,
            ),
        )
    }

    /** Quita del cuerpo una foto: fuera de la tabla y fuera del disco. */
    suspend fun removeNoteBodyImage(noteId: Long, storedPath: String) {
        dao.deleteImageByPath(noteId, storedPath)
        withContext(Dispatchers.IO) { File(storedPath).delete() }
    }

    suspend fun deleteBodyImageFile(file: File) {
        withContext(Dispatchers.IO) { file.delete() }
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
     * Empaqueta la nota y sus fotos en un ZIP: el documento en la raíz y las
     * fotos en [photosFolderName], que es lo que enlaza el documento.
     */
    suspend fun writeNoteExportZip(
        destinationUri: Uri,
        documentName: String,
        document: String,
        photosFolderName: String,
        photos: List<NoteExportPhoto>,
    ) {
        withContext(Dispatchers.IO) {
            // "wt" (API 26+) trunca si el destino ya existía; sin eso un ZIP a
            // medias puede quedar con basura de una escritura anterior.
            val out = if (android.os.Build.VERSION.SDK_INT >= 26) {
                appContext.contentResolver.openOutputStream(destinationUri, "wt")
            } else {
                appContext.contentResolver.openOutputStream(destinationUri)
            } ?: throw IOException("No se pudo escribir en el destino elegido.")
            ZipOutputStream(BufferedOutputStream(out)).use { zos ->
                zos.putNextEntry(ZipEntry(documentName))
                zos.write(document.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
                for (photo in photos) {
                    if (!photo.source.isFile) continue
                    zos.putNextEntry(ZipEntry("$photosFolderName/${photo.fileName}"))
                    photo.source.inputStream().use { input -> input.copyTo(zos) }
                    zos.closeEntry()
                }
                zos.finish()
            }
        }
    }

    /**
     * Escribe la nota en la carpeta elegida junto a una subcarpeta con sus fotos.
     *
     * SAF renombra lo que ya existe, así que la subcarpeta se crea primero y el
     * documento se compone después con [buildDocument], ya con el nombre real;
     * si no, los enlaces relativos apuntarían a una carpeta que no es. La
     * subcarpeta recién creada está vacía, de modo que los nombres de las fotos
     * sí se respetan.
     */
    suspend fun writeNoteExportFiles(
        treeUri: Uri,
        documentName: String,
        mimeType: String,
        photosFolderName: String,
        photos: List<NoteExportPhoto>,
        buildDocument: (photosFolderName: String) -> String,
    ) {
        withContext(Dispatchers.IO) {
            val resolver = appContext.contentResolver
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )

            val existingPhotos = photos.filter { it.source.isFile }
            val photosFolder = if (existingPhotos.isEmpty()) {
                null
            } else {
                DocumentsContract.createDocument(
                    resolver,
                    parentUri,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    photosFolderName,
                ) ?: throw IOException("No se pudo crear la carpeta de fotos.")
            }
            val folderName = photosFolder?.let { displayName(it) } ?: photosFolderName

            val documentUri = DocumentsContract.createDocument(
                resolver,
                parentUri,
                mimeType,
                documentName,
            ) ?: throw IOException("No se pudo crear el archivo de la nota.")

            resolver.openOutputStream(documentUri)?.use { out ->
                out.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                    writer.write(buildDocument(folderName))
                }
            } ?: throw IOException("No se pudo escribir la nota.")

            if (photosFolder != null) {
                for (photo in existingPhotos) {
                    val photoUri = DocumentsContract.createDocument(
                        resolver,
                        photosFolder,
                        "image/jpeg",
                        photo.fileName,
                    ) ?: throw IOException("No se pudo crear ${photo.fileName}.")
                    resolver.openOutputStream(photoUri)?.use { out ->
                        photo.source.inputStream().use { input -> input.copyTo(out) }
                    } ?: throw IOException("No se pudo copiar ${photo.fileName}.")
                }
            }
        }
    }

    private fun displayName(uri: Uri): String? =
        appContext.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
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
            // El uid estable viaja como exportId: al restaurar los enlaces internos
            // siguen apuntando a la misma nota.
            val exportId = nwt.note.uid.ifBlank { UUID.randomUUID().toString() }
            // Las fotos incrustadas se localizan por el texto, que es lo único que
            // dice dónde va cada una; hay que reapuntar sus enlaces al ZIP.
            val body = relocateBodyPhotos(
                ensurePureMarkdown(nwt.note.content),
                "body/$exportId",
            )
            val bodyImagesJson = JSONArray()
            for (photo in body.photos) {
                val zipPath = "body/$exportId/${photo.fileName}"
                bodyImagesJson.put(zipPath)
                imageWrites.add(zipPath to photo.source)
            }

            val noteObj = JSONObject()
            noteObj.put("exportId", exportId)
            noteObj.put("title", nwt.note.title)
            noteObj.put("content", body.content)
            noteObj.put("bodyImages", bodyImagesJson)
            noteObj.put("createdAtMillis", nwt.note.createdAtMillis)
            noteObj.put("updatedAtMillis", nwt.note.updatedAtMillis)
            val tagsArr = JSONArray()
            nwt.tags.forEach { tagsArr.put(it.name) }
            noteObj.put("tags", tagsArr)

            // Las fotos del cuerpo están en note_images y ya viajan en `body/`:
            // sin esto irían dos veces en el ZIP.
            val bodyPhotoPaths = body.photos.map { it.source.absolutePath }.toSet()
            val imagesJson = JSONArray()
            val images = dao.getImagesForNoteExport(nwt.note.id)
            var fileIndex = 0
            for (img in images) {
                if (img.storedPath in bodyPhotoPaths) continue
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

            // exportId del ZIP → uid real insertado (si chocaba, se regenera).
            val uidMap = mutableMapOf<String, String>()
            data class PendingNote(
                val preferredUid: String,
                val title: String,
                val content: String,
                val created: Long,
                val updated: Long,
                val json: JSONObject,
            )

            suspend fun allocateUid(preferred: String): String {
                val taken = uidMap.values.toHashSet()
                if (preferred !in taken && dao.getNoteIdByUid(preferred) == null) {
                    return preferred
                }
                var candidate: String
                do {
                    candidate = UUID.randomUUID().toString()
                } while (candidate in taken || dao.getNoteIdByUid(candidate) != null)
                return candidate
            }

            val pending = mutableListOf<PendingNote>()
            for (i in 0 until notesArr.length()) {
                val n = notesArr.getJSONObject(i)
                val preferred = n.optString("exportId", "").ifBlank { UUID.randomUUID().toString() }
                val uid = allocateUid(preferred)
                uidMap[preferred] = uid
                pending += PendingNote(
                    preferredUid = preferred,
                    title = n.optString("title", ""),
                    content = n.optString("content", ""),
                    created = n.optLong("createdAtMillis", System.currentTimeMillis()),
                    updated = n.optLong("updatedAtMillis", System.currentTimeMillis()),
                    json = n,
                )
            }

            var imported = 0
            for (item in pending) {
                val n = item.json
                val uid = uidMap.getValue(item.preferredUid)
                var content = remapNoteLinkUids(item.content, uidMap)
                val noteId = dao.insertNote(
                    NoteEntity(
                        id = 0,
                        uid = uid,
                        title = item.title,
                        content = content,
                        createdAtMillis = item.created,
                        updatedAtMillis = item.updated,
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
                // Las fotos del cuerpo vuelven al almacenamiento interno y sus
                // enlaces relativos pasan a apuntar a la copia recién hecha.
                val bodyImages = n.optJSONArray("bodyImages")
                if (bodyImages != null && bodyImages.length() > 0) {
                    val dir = File(appContext.filesDir, "note_images/$noteId").apply { mkdirs() }
                    for (j in 0 until bodyImages.length()) {
                        val path = bodyImages.getString(j)
                        val src = resolveZipEntrySafe(stagingDir, path)
                        if (!src.isFile) continue
                        val dest = File(dir, "${UUID.randomUUID()}.jpg")
                        src.inputStream().use { inp ->
                            dest.outputStream().use { out -> inp.copyTo(out) }
                        }
                        registerNoteBodyImage(noteId, dest)
                        content = content.replace(path, "file://${dest.absolutePath}")
                    }
                    dao.updateNote(
                        NoteEntity(
                            id = noteId,
                            uid = uid,
                            title = item.title,
                            content = content,
                            createdAtMillis = item.created,
                            updatedAtMillis = item.updated,
                        ),
                    )
                }

                val images = n.optJSONArray("images")
                if (images != null) {
                    val pairs = mutableListOf<Pair<Int, String>>()
                    for (j in 0 until images.length()) {
                        val im = images.getJSONObject(j)
                        pairs.add(im.optInt("sortOrder", j) to im.getString("path"))
                    }
                    pairs.sortBy { it.first }
                    // Las fotos del cuerpo ya ocupan las primeras posiciones.
                    var order = dao.getImagesForNoteExport(noteId)
                        .maxOfOrNull { it.sortOrder }?.plus(1) ?: 0
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
                syncNoteLinks(noteId, content)
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
