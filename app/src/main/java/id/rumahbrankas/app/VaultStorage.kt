package id.rumahbrankas.app

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Small file-system boundary for the encrypted vault. */
internal class VaultStorage(
    filesDir: File,
    val cacheDir: File,
    private val writer: AtomicFileWriter,
) {
    val vaultDir = File(filesDir, "vault").apply { mkdirs() }
    private val metadataFile = File(vaultDir, "vault.meta")
    private val indexFile = File(vaultDir, "index.enc")
    private val logFile = File(filesDir, "brankas-errors.log")
    private val previousLogFile = File(filesDir, "brankas-errors.previous.log")

    fun hasMetadata(): Boolean = metadataFile.exists()
    fun readMetadata(): JSONObject = JSONObject(metadataFile.readText())
    fun writeMetadata(value: JSONObject) = writer.write(metadataFile, value.toString().toByteArray(Charsets.UTF_8))
    fun readEncryptedIndex(): ByteArray? = indexFile.takeIf(File::exists)?.readBytes()
    fun writeEncryptedIndex(value: ByteArray) = writer.write(indexFile, value)
    fun writeEncryptedNote(id: String, value: ByteArray) = writer.write(itemFile(id, "note.vlt"), value)
    fun deleteEncryptedIndex() { indexFile.delete() }

    fun itemFile(id: String, extension: String): File {
        require(id.matches(Regex("[A-Za-z0-9-]{1,80}"))) { "ID item tidak valid." }
        require(extension.matches(Regex("[A-Za-z0-9.]{1,24}"))) { "Jenis berkas tidak valid." }
        return File(vaultDir, "$id.$extension")
    }

    fun previewFile(name: String): File = File(cacheDir, name)

    fun clearPreviewCache(): Boolean {
        val previews = cacheDir.listFiles { file -> file.name.startsWith("brankas-preview-") }
            ?: return false
        previews.forEach { file -> if (file.exists()) file.delete() }
        return previews.none(File::exists)
    }

    @Synchronized
    fun appendFailure(line: String) {
        if (logFile.length() > 512L * 1024L) {
            previousLogFile.delete()
            logFile.renameTo(previousLogFile)
        }
        logFile.appendText(line + System.lineSeparator(), Charsets.UTF_8)
    }
}

/** Owns encrypted index JSON and performs the one-time migration from legacy inline notes. */
internal class EncryptedVaultIndexStore(
    private val storage: VaultStorage,
    private val notes: EncryptedVaultNoteStore,
) {
    fun read(masterKey: ByteArray): List<VaultEntry> {
        val encrypted = storage.readEncryptedIndex() ?: return emptyList()
        val clear = VaultCrypto.decryptCombined(masterKey, encrypted)
        try {
            val array = JSONArray(clear.toString(Charsets.UTF_8))
            val entries = ArrayList<VaultEntry>(array.length())
            val legacyNotes = LinkedHashMap<String, String>()
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val entry = VaultEntry(
                    id = item.getString("id"),
                    title = item.getString("title"),
                    mimeType = item.getString("mime"),
                    createdAt = item.getLong("created"),
                )
                entries += entry
                if (entry.isNote && item.has("note")) {
                    legacyNotes[entry.id] = item.optString("note")
                }
            }

            // Persist each note before dropping its legacy body from index.enc.
            // If any write fails, the old encrypted index remains recoverable.
            if (legacyNotes.isNotEmpty()) {
                legacyNotes.forEach { (id, body) -> notes.write(masterKey, id, body.toByteArray(Charsets.UTF_8)) }
                write(masterKey, entries)
            }
            return entries.sortedByDescending(VaultEntry::createdAt)
        } finally {
            clear.fill(0)
        }
    }

    fun write(masterKey: ByteArray, entries: List<VaultEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("title", entry.title)
                    .put("mime", entry.mimeType)
                    .put("created", entry.createdAt),
            )
        }
        storage.writeEncryptedIndex(
            VaultCrypto.encryptCombined(masterKey, array.toString().toByteArray(Charsets.UTF_8)),
        )
    }
}

/** Each note body is independently authenticated and encrypted at rest. */
internal class EncryptedVaultNoteStore(private val storage: VaultStorage) {
    fun write(masterKey: ByteArray, id: String, body: ByteArray) {
        val encrypted = VaultCrypto.encryptCombined(masterKey, body)
        try {
            storage.writeEncryptedNote(id, encrypted)
        } finally {
            encrypted.fill(0)
        }
    }

    fun read(masterKey: ByteArray, id: String): ByteArray =
        VaultCrypto.decryptCombined(masterKey, storage.itemFile(id, "note.vlt").readBytes())

    fun delete(id: String): Boolean {
        val file = storage.itemFile(id, "note.vlt")
        return file.delete() || !file.exists()
    }
}
