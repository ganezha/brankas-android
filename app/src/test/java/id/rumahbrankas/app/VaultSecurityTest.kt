package id.rumahbrankas.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.crypto.AEADBadTagException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VaultSecurityTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun incorrectPinNeverReturnsMasterKey() {
        val repo = repository("wrong-pin")
        val created = createVault(repo)
        try {
            assertThrows(Exception::class.java) { repo.unlock("654321".toCharArray()) }
        } finally {
            created.masterKey.fill(0)
        }
    }

    @Test
    fun changingOneByteInVaultFileFailsAuthentication() {
        val repo = repository("tamper-vault")
        val created = createVault(repo)
        try {
            val documents = FakeDocuments(byteArrayOf(1, 4, 9, 16, 25))
            val imported = repo.importAndMove(created.masterKey, documents, SOURCE)
            val encryptedFile = File(vaultDirectory("tamper-vault"), imported.entry.id + ".vlt")
            val bytes = encryptedFile.readBytes()
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            encryptedFile.writeBytes(bytes)
            assertThrows(AEADBadTagException::class.java) {
                repo.decryptToCache(created.masterKey, imported.entry)
            }
        } finally {
            created.masterKey.fill(0)
        }
    }

    @Test
    fun changingOneByteInEncryptedIndexFailsAuthentication() {
        val repo = repository("tamper-index")
        val created = createVault(repo)
        try {
            repo.addNote(created.masterKey, "Catatan", "isi")
            val index = File(vaultDirectory("tamper-index"), "index.enc")
            val bytes = index.readBytes()
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            index.writeBytes(bytes)
            assertThrows(AEADBadTagException::class.java) { repo.entries(created.masterKey) }
        } finally {
            created.masterKey.fill(0)
        }
    }

    @Test
    fun legacyInlineNotesMigrateToSeparateEncryptedFilesWithoutLosingContent() {
        val repo = repository("legacy-note-migration")
        val created = createVault(repo)
        try {
            val id = "7a30ad3b-6dfd-486d-9f37-635039ccff62"
            val body = "catatan lama yang harus tetap rahasia"
            val legacy = JSONArray().put(
                JSONObject()
                    .put("id", id)
                    .put("title", "Catatan lama")
                    .put("mime", "text/plain")
                    .put("created", 123L)
                    .put("note", body),
            )
            File(vaultDirectory("legacy-note-migration"), "index.enc").writeBytes(
                VaultCrypto.encryptCombined(created.masterKey, legacy.toString().toByteArray(Charsets.UTF_8)),
            )

            val migrated = repo.entries(created.masterKey).single()
            assertEquals(id, migrated.id)
            assertEquals(body, repo.readNote(created.masterKey, migrated))
            assertTrue(File(vaultDirectory("legacy-note-migration"), "$id.note.vlt").exists())

            val newIndex = VaultCrypto.decryptCombined(
                created.masterKey,
                File(vaultDirectory("legacy-note-migration"), "index.enc").readBytes(),
            ).toString(Charsets.UTF_8)
            assertTrue(newIndex.contains("\"id\""))
            assertFalse(newIndex.contains(body))
            assertFalse(newIndex.contains("\"note\""))
        } finally {
            created.masterKey.fill(0)
        }
    }

    @Test
    fun interruptedImportKeepsOriginalAndRemovesStagingFile() {
        val repo = repository("interrupted-import")
        val created = createVault(repo)
        try {
            val original = byteArrayOf(3, 7, 11, 19, 27, 31, 44, 58)
            val documents = FakeDocuments(original) { FailingInputStream(original, 4) }
            assertThrows(IOException::class.java) {
                repo.importAndMove(created.masterKey, documents, SOURCE)
            }
            assertEquals(0, documents.deleteCalls)
            assertTrue(documents.originalContent.contentEquals(original))
            assertTrue(repo.entries(created.masterKey).isEmpty())
            assertTrue(vaultDirectory("interrupted-import").listFiles()!!
                .none { it.name.endsWith(".importing") || it.name.endsWith(".vlt") })
        } finally {
            created.masterKey.fill(0)
        }
    }

    @Test
    fun restoreHashMismatchLeavesVaultItemAndCiphertextIntact() {
        val repo = repository("restore-hash")
        val created = createVault(repo)
        try {
            val documents = FakeDocuments(byteArrayOf(10, 20, 30, 40))
            val imported = repo.importAndMove(created.masterKey, documents, SOURCE)
            documents.readbackTransform = {
                it.copyOf().also { bytes -> bytes[0] = (bytes[0].toInt() xor 1).toByte() }
            }
            assertThrows(IllegalStateException::class.java) {
                repo.restoreAndMove(created.masterKey, imported.entry, documents, DESTINATION)
            }
            assertEquals(listOf(imported.entry), repo.entries(created.masterKey))
            assertTrue(File(vaultDirectory("restore-hash"), imported.entry.id + ".vlt").exists())
        } finally {
            created.masterKey.fill(0)
        }
    }

    @Test
    fun recoveryResetInvalidatesOldPinAndAcceptsNewPin() {
        val repo = repository("pin-reset")
        val created = createVault(repo)
        var recovered = byteArrayOf()
        try {
            recovered = repo.unlockWithRecovery(created.recoveryCode.toCharArray())
            assertTrue(created.masterKey.contentEquals(recovered))
            repo.resetCredential(recovered, "987654".toCharArray(), "pin")
            val newPinUnlock = repo.unlock("987654".toCharArray())
            try {
                assertTrue(created.masterKey.contentEquals(newPinUnlock))
            } finally {
                newPinUnlock.fill(0)
            }
            assertThrows(Exception::class.java) { repo.unlock("123456".toCharArray()) }
        } finally {
            recovered.fill(0)
            created.masterKey.fill(0)
        }
    }

    @Test
    fun interruptedIndexNextWritePreservesCommittedIndex() {
        val filesDir = temporaryFolder.newFolder("atomic-index")
        val cacheDir = File(filesDir, "cache").apply { mkdirs() }
        val initial = DefaultVaultRepository(filesDir, cacheDir)
        val created = createVault(initial)
        try {
            val first = initial.addNote(created.masterKey, "Tetap ada", "catatan awal")
            val indexFile = File(vaultDirectory("atomic-index"), "index.enc")
            val committedBefore = indexFile.readBytes()
            val failingWriter = AtomicFileWriter(writeStaged = { staged, bytes ->
                if (staged.name.startsWith("index.enc")) {
                    FileOutputStream(staged).use { output ->
                        output.write(bytes, 0, (bytes.size / 2).coerceAtLeast(1))
                    }
                    throw IOException("simulated staged-write interruption")
                }
                FileOutputStream(staged).use { output -> output.write(bytes) }
            })
            val interrupted = DefaultVaultRepository(filesDir, cacheDir, failingWriter)
            assertThrows(IOException::class.java) {
                interrupted.addNote(created.masterKey, "Jangan disimpan", "penulisan terputus")
            }
            assertTrue(committedBefore.contentEquals(indexFile.readBytes()))
            assertFalse(File(vaultDirectory("atomic-index"), "index.enc.next").exists())
            assertEquals(listOf(first), initial.entries(created.masterKey))
        } finally {
            created.masterKey.fill(0)
        }
    }

    @Test
    fun authenticatedEncryptionRejectsTamperedPayloadAndWrongKey() {
        val key = ByteArray(32) { it.toByte() }
        val payload = "konten privat".toByteArray(Charsets.UTF_8)
        val encrypted = VaultCrypto.encryptCombined(key, payload)
        try {
            assertTrue(payload.contentEquals(VaultCrypto.decryptCombined(key, encrypted)))
            val tampered = encrypted.copyOf().also {
                it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
            }
            assertThrows(AEADBadTagException::class.java) {
                VaultCrypto.decryptCombined(key, tampered)
            }
            assertThrows(AEADBadTagException::class.java) {
                VaultCrypto.decryptCombined(ByteArray(32) { (it + 1).toByte() }, encrypted)
            }
        } finally {
            key.fill(0)
        }
    }

    private fun repository(name: String): DefaultVaultRepository {
        val files = temporaryFolder.newFolder(name)
        return DefaultVaultRepository(files, File(files, "cache").apply { mkdirs() })
    }

    private fun createVault(repo: VaultRepository): CreatedVault {
        val created = repo.create("123456".toCharArray(), "pin")
        repo.commitCreate(created)
        return created
    }

    private fun vaultDirectory(name: String): File =
        File(temporaryFolder.root, name + File.separator + "vault")

    private class FakeDocuments(
        source: ByteArray,
        private val sourceStream: () -> InputStream = { ByteArrayInputStream(source) },
    ) : VaultDocumentAccess {
        val originalContent = source.copyOf()
        var deleteCalls = 0
            private set
        var readbackTransform: (ByteArray) -> ByteArray = { it.copyOf() }
        private val destinationContent = ByteArrayOutputStream()

        override fun displayName(reference: String): String = "sample.jpg"
        override fun mimeType(reference: String): String = "image/jpeg"
        override fun openInputStream(reference: String): InputStream =
            if (reference == SOURCE) sourceStream()
            else ByteArrayInputStream(readbackTransform(destinationContent.toByteArray()))

        override fun openOutputStream(reference: String, mode: String): OutputStream {
            destinationContent.reset()
            return destinationContent
        }

        override fun delete(reference: String): Boolean {
            deleteCalls++
            return true
        }
    }

    private class FailingInputStream(
        private val bytes: ByteArray,
        private val bytesBeforeFailure: Int,
    ) : InputStream() {
        private var position = 0

        override fun read(): Int {
            if (position >= bytesBeforeFailure) throw IOException("simulated source interruption")
            if (position >= bytes.size) return -1
            return bytes[position++].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= bytesBeforeFailure) throw IOException("simulated source interruption")
            if (position >= bytes.size) return -1
            val count = minOf(length, bytesBeforeFailure - position, bytes.size - position)
            bytes.copyInto(buffer, offset, position, position + count)
            position += count
            return count
        }
    }

    private companion object {
        const val SOURCE = "source://document"
        const val DESTINATION = "destination://document"
    }
}
