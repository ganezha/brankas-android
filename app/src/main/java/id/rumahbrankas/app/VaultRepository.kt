package id.rumahbrankas.app

import android.content.ContentResolver
import android.content.Context
import android.util.Log
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class CreatedVault(val masterKey: ByteArray, val recoveryCode: String, internal val pendingMetadata: String, val credentialKind: String)

private class InvalidCredentialException(val credentialKind: String) : Exception()
private class PinAttemptDelay(val remainingSeconds: Long) : Exception()

data class VaultEntry(
    val id: String,
    val title: String,
    val mimeType: String,
    val createdAt: Long,
) {
    val isNote: Boolean get() = mimeType == "text/plain"
    val isVideo: Boolean get() = mimeType.startsWith("video/")
}

data class ImportOutcome(val entry: VaultEntry, val sourceRemoved: Boolean)

/** All user content is encrypted before it reaches persistent app storage. */
class DefaultVaultRepository internal constructor(
    private val filesDir: File,
    private val cacheDir: File,
    private val atomicFileWriter: AtomicFileWriter = AtomicFileWriter(),
) : VaultRepository {
    private var appContext: Context? = null

    constructor(context: Context) : this(context.applicationContext.filesDir, context.applicationContext.cacheDir) {
        appContext = context.applicationContext
    }

    private val storage = VaultStorage(filesDir, cacheDir, atomicFileWriter)
    private val noteStore = EncryptedVaultNoteStore(storage)
    private val indexStore = EncryptedVaultIndexStore(storage, noteStore)
    private val random = SecureRandom()
    private val bioAlias = "brankas-biometric-v1"

    init { clearPreviewCache() }

    override fun exists(): Boolean = storage.hasMetadata()

    override fun create(secret: CharArray, credentialKind: String): CreatedVault {
        val master = ByteArray(32).also(random::nextBytes)
        try {
            validateCredential(secret, credentialKind)
            val credentialSalt = ByteArray(16).also(random::nextBytes)
            val credentialDerived = deriveKey(secret, credentialSalt)
            val credentialWrap = try { encrypt(credentialDerived, master) } finally { credentialDerived.fill(0) }
            val recovery = ByteArray(20).also(random::nextBytes).toHex().uppercase()
            val recoverySalt = ByteArray(16).also(random::nextBytes)
            val recoveryDerived = deriveKey(recovery.toCharArray(), recoverySalt)
            val recoveryWrap = try { encrypt(recoveryDerived, master) } finally { recoveryDerived.fill(0) }
            val meta = JSONObject()
                .put("version", 2)
                .put("credentialType", credentialKind)
                .put("pinSalt", credentialSalt.hex())
                .put("pinIv", credentialWrap.first.hex())
                .put("pinKey", credentialWrap.second.hex())
                .put("recoverySalt", recoverySalt.hex())
                .put("recoveryIv", recoveryWrap.first.hex())
                .put("recoveryKey", recoveryWrap.second.hex())
                .put("failedPinAttempts", 0)
                .put("nextPinAttemptAt", 0L)
            return CreatedVault(master, recovery.chunked(4).joinToString("-"), meta.toString(), credentialKind)
        } catch (error: Exception) {
            master.fill(0)
            throw error
        } finally {
            secret.fill('\u0000')
        }
    }

    @Synchronized
    override fun commitCreate(created: CreatedVault) {
        check(!exists()) { "Brankas sudah dibuat." }
        saveEntries(created.masterKey, emptyList())
        try {
            writeMeta(JSONObject(created.pendingMetadata))
        } catch (error: Exception) {
            storage.deleteEncryptedIndex()
            throw error
        }
    }

    override fun credentialType(): String = readMeta().optString("credentialType", "pin")
        .takeIf { it == "passphrase" } ?: "pin"

    override fun unlock(secret: CharArray): ByteArray = try {
        unwrap("pin", secret)
    } finally {
        secret.fill('\u0000')
    }

    override fun unlockWithRecovery(code: CharArray): ByteArray {
        val normalized = code.concatToString().filter { it.isLetterOrDigit() }.uppercase().toCharArray()
        code.fill('\u0000')
        return try {
            unwrap("recovery", normalized)
        } finally {
            normalized.fill('\u0000')
        }
    }

    override fun resetCredential(master: ByteArray, newSecret: CharArray, credentialKind: String) {
        try {
            validateCredential(newSecret, credentialKind)
            val meta = readMeta()
            val salt = ByteArray(16).also(random::nextBytes)
            val derived = deriveKey(newSecret, salt)
            val wrapped = try { encrypt(derived, master) } finally { derived.fill(0) }
            meta.put("credentialType", credentialKind)
                .put("pinSalt", salt.hex())
                .put("pinIv", wrapped.first.hex())
                .put("pinKey", wrapped.second.hex())
                .put("failedPinAttempts", 0)
                .put("nextPinAttemptAt", 0L)
            writeMeta(meta)
        } finally {
            newSecret.fill('\u0000')
        }
    }

    private fun validateCredential(secret: CharArray, credentialKind: String) {
        when (credentialKind) {
            "pin" -> require(secret.size in 6..12 && secret.all { it in '0'..'9' }) { "PIN harus 6–12 digit angka." }
            "passphrase" -> require(secret.size in 12..128) { "Frasa sandi harus 12–128 karakter." }
            else -> throw IllegalArgumentException("Jenis kredensial tidak dikenal.")
        }
    }

    override fun entries(master: ByteArray): List<VaultEntry> = indexStore.read(master)

    override fun readNote(master: ByteArray, entry: VaultEntry): String {
        require(entry.isNote) { "Item ini bukan catatan." }
        val clear = noteStore.read(master, entry.id)
        return try { clear.toString(Charsets.UTF_8) } finally { clear.fill(0) }
    }

    override fun addNote(master: ByteArray, title: String, text: String): VaultEntry {
        val resolvedTitle = title.trim().ifBlank {
            appContext?.getString(R.string.default_note_title) ?: "Catatan baru"
        }
        val item = VaultEntry(
            java.util.UUID.randomUUID().toString(),
            resolvedTitle,
            "text/plain",
            System.currentTimeMillis(),
        )
        val body = text.toByteArray(Charsets.UTF_8)
        try {
            noteStore.write(master, item.id, body)
            try {
                saveEntries(master, entries(master) + item)
            } catch (error: Exception) {
                noteStore.delete(item.id)
                throw error
            }
            return item
        } finally {
            body.fill(0)
        }
    }

    override fun importAndMove(master: ByteArray, uri: String): ImportOutcome {
        val context = context()
        return importAndMove(master, ContentResolverDocumentAccess(context, context.contentResolver), uri)
    }

    internal fun importAndMove(master: ByteArray, documents: VaultDocumentAccess, uri: String): ImportOutcome {
        val displayName = documents.displayName(uri) ?: "Media " + System.currentTimeMillis()
        val mime = documents.mimeType(uri) ?: "application/octet-stream"
        val id = java.util.UUID.randomUUID().toString()
        val file = storage.itemFile(id, "vlt")
        val staging = storage.itemFile(id, "importing")
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(master, "AES"), GCMParameterSpec(128, iv))
        val sourceDigest = MessageDigest.getInstance("SHA-256")
        try {
            documents.openInputStream(uri)?.use { input ->
                FileOutputStream(staging).use { output ->
                    output.write(iv)
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        sourceDigest.update(buffer, 0, count)
                        val encrypted = cipher.update(buffer, 0, count)
                        if (encrypted != null) output.write(encrypted)
                    }
                    output.write(cipher.doFinal())
                    output.fd.sync()
                }
            } ?: throw IllegalStateException("File sumber tidak dapat dibaca.")

            // Verifikasi tag GCM dan hash isi sebelum menyentuh file sumber.
            verifyEncryptedFile(staging, master, sourceDigest.digest())
            if (!staging.renameTo(file)) throw IllegalStateException("File terenkripsi belum dapat disimpan.")

            val item = VaultEntry(id, displayName, mime, System.currentTimeMillis())
            try {
                saveEntries(master, entries(master) + item)
            } catch (error: Exception) {
                file.delete()
                throw error
            }

            // SAF grants write access only for some providers. A denied delete is
            // reported honestly and the original is kept intact.
            val sourceRemoved = try {
                documents.delete(uri)
            } catch (error: Exception) {
                logFailure("Penghapusan file sumber setelah impor", error)
                false
            }

            return ImportOutcome(item, sourceRemoved)
        } catch (error: Exception) {
            staging.delete()
            throw error
        }
    }

    /** Writes a verified plaintext copy to a user-selected destination, then removes it from the vault. */
    override fun restoreAndMove(master: ByteArray, entry: VaultEntry, destination: String): Boolean {
        val context = context()
        return restoreAndMove(master, entry, ContentResolverDocumentAccess(context, context.contentResolver), destination)
    }

    internal fun restoreAndMove(
        master: ByteArray,
        entry: VaultEntry,
        documents: VaultDocumentAccess,
        destination: String,
    ): Boolean {
        val source = if (entry.isNote) null else decryptToCache(master, entry)
        val clearBytes = if (entry.isNote) noteStore.read(master, entry.id) else null
        val expectedLength = source?.length() ?: clearBytes!!.size.toLong()
        val expectedDigest = MessageDigest.getInstance("SHA-256")
        if (source != null) FileInputStream(source).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                expectedDigest.update(buffer, 0, count)
            }
        }
        else expectedDigest.update(clearBytes!!)
        val hash = expectedDigest.digest()

        try {
            val output = documents.openOutputStream(destination, "wt")
                ?: throw IllegalStateException("Lokasi tujuan tidak dapat ditulis.")
            var written = 0L
            output.use { sink ->
                if (source != null) FileInputStream(source).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        sink.write(buffer, 0, count)
                        written += count
                    }
                } else {
                    sink.write(clearBytes!!)
                    written = clearBytes.size.toLong()
                }
                sink.flush()
            }
            if (written != expectedLength) throw IllegalStateException("File tujuan belum tersimpan lengkap.")

            val actualDigest = MessageDigest.getInstance("SHA-256")
            var actualLength = 0L
            documents.openInputStream(destination)?.use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    actualDigest.update(buffer, 0, count)
                    actualLength += count
                }
            } ?: throw IllegalStateException("Hasil pemulihan tidak dapat diverifikasi.")
            if (actualLength != expectedLength || !MessageDigest.isEqual(hash, actualDigest.digest())) {
                throw IllegalStateException("Hasil pemulihan tidak cocok; item tetap disimpan di Brankas.")
            }

            return removeEntryAfterVerifiedRestore(master, entry)
        } finally {
            source?.delete()
            clearBytes?.fill(0)
        }
    }

    private fun verifyEncryptedFile(file: File, master: ByteArray, expectedHash: ByteArray) {
        FileInputStream(file).use { input ->
            val iv = ByteArray(12)
            var offset = 0
            while (offset < iv.size) {
                val count = input.read(iv, offset, iv.size - offset)
                if (count < 0) throw IllegalStateException("Data terenkripsi tidak lengkap.")
                offset += count
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(master, "AES"), GCMParameterSpec(128, iv))
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                cipher.update(buffer, 0, count)?.let(digest::update)
            }
            cipher.doFinal().let(digest::update)
            if (!MessageDigest.isEqual(expectedHash, digest.digest())) {
                throw IllegalStateException("Verifikasi enkripsi gagal.")
            }
        }
    }

    override fun decryptToCache(master: ByteArray, entry: VaultEntry): File {
        require(!entry.isNote) { "Catatan dibaca sebagai teks." }
        val encryptedFile = storage.itemFile(entry.id, "vlt")
        val safeName = entry.title.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val outputFile = storage.previewFile("brankas-preview-${entry.id}-${java.util.UUID.randomUUID()}-$safeName")
        try {
            FileInputStream(encryptedFile).use { input ->
                val iv = ByteArray(12)
                if (input.read(iv) != iv.size) throw IllegalStateException("File tidak lengkap.")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(master, "AES"), GCMParameterSpec(128, iv))
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        cipher.update(buffer, 0, count)?.let(output::write)
                    }
                    cipher.doFinal().let(output::write)
                    output.fd.sync()
                }
            }
        } catch (error: Exception) {
            outputFile.delete()
            throw error
        }
        return outputFile
    }

    override fun clearPreviewCache(): Boolean = storage.clearPreviewCache()

    override fun exportBackup(destination: String) {
        val documents = ContentResolverDocumentAccess(context(), context().contentResolver)
        val output = documents.openOutputStream(destination, "wt")
            ?: throw java.io.IOException("Tujuan cadangan tidak dapat ditulis.")
        output.use { exportBackupTo(it) }
    }

    override fun restoreBackup(source: String) {
        val documents = ContentResolverDocumentAccess(context(), context().contentResolver)
        val input = documents.openInputStream(source)
            ?: throw java.io.IOException("Cadangan tidak dapat dibaca.")
        input.use { restoreBackupFrom(it) }
    }

    internal fun exportBackupTo(output: OutputStream) {
        VaultArchive.export(storage.vaultDir, output)
    }

    internal fun restoreBackupFrom(input: InputStream) {
        VaultArchive.restore(input, storage.vaultDir)
    }

    override fun delete(master: ByteArray, entry: VaultEntry) {
        saveEntries(master, entries(master).filterNot { it.id == entry.id })
        if (entry.isNote) noteStore.delete(entry.id) else storage.itemFile(entry.id, "vlt").delete()
    }

    private fun removeEntryAfterVerifiedRestore(master: ByteArray, entry: VaultEntry): Boolean {
        saveEntries(master, entries(master).filterNot { it.id == entry.id })
        return if (entry.isNote) {
            noteStore.delete(entry.id)
        } else {
            val file = storage.itemFile(entry.id, "vlt")
            file.delete() || !file.exists()
        }
    }

    override fun hasBiometric(): Boolean = readMeta().has("bioIv") && KeyStore.getInstance("AndroidKeyStore").run { load(null); containsAlias(bioAlias) }

    override fun newBiometricEncryptCipher(): Cipher {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(bioAlias)) {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(bioAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(true)
                    .setInvalidatedByBiometricEnrollment(true)
                    .build(),
            )
            generator.generateKey()
        }
        val key = store.getKey(bioAlias, null) as SecretKey
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    override fun saveBiometricWrap(cipher: Cipher, master: ByteArray) {
        val wrapped = cipher.doFinal(master)
        readMeta().put("bioIv", cipher.iv.hex()).put("bioKey", wrapped.hex()).also(::writeMeta)
    }

    override fun biometricDecryptCipher(): Cipher {
        val meta = readMeta()
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = store.getKey(bioAlias, null) as SecretKey
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, meta.getString("bioIv").unhex()))
        }
    }

    override fun finishBiometricUnlock(cipher: Cipher): ByteArray = cipher.doFinal(readMeta().getString("bioKey").unhex())

    private fun unwrap(kind: String, passphrase: CharArray): ByteArray {
        val meta = readMeta()
        val storedKind = meta.optString("credentialType", "pin")
        if (kind == "pin" && storedKind == "pin") checkPinAttemptDelay(meta)
        val key = deriveKey(passphrase, meta.getString("${kind}Salt").unhex())
        try {
            val result = try {
                decrypt(key, meta.getString("${kind}Iv").unhex(), meta.getString("${kind}Key").unhex())
            } catch (_: AEADBadTagException) {
                if (kind == "pin" && storedKind == "pin") recordFailedPinAttempt(meta)
                throw InvalidCredentialException(if (kind == "recovery") "recovery" else storedKind)
            }
            if (kind == "pin" && storedKind == "pin") clearFailedPinAttempts(meta)
            return result
        } finally {
            key.fill(0)
        }
    }

    private fun checkPinAttemptDelay(meta: JSONObject) {
        val remainingMillis = meta.optLong("nextPinAttemptAt", 0L) - System.currentTimeMillis()
        if (remainingMillis > 0L) throw PinAttemptDelay((remainingMillis + 999L) / 1000L)
    }

    private fun recordFailedPinAttempt(meta: JSONObject) {
        try {
            val attempts = (meta.optInt("failedPinAttempts", 0) + 1).coerceAtMost(20)
            val exponent = (attempts - 1).coerceAtMost(10)
            val delayMillis = (1_000L shl exponent).coerceAtMost(15L * 60L * 1_000L)
            meta.put("failedPinAttempts", attempts)
                .put("nextPinAttemptAt", System.currentTimeMillis() + delayMillis)
            writeMeta(meta)
        } catch (error: Exception) {
            logFailure("Pencatatan percobaan PIN", error)
        }
    }

    private fun clearFailedPinAttempts(meta: JSONObject) {
        if (meta.optInt("failedPinAttempts", 0) == 0 && meta.optLong("nextPinAttemptAt", 0L) == 0L) return
        try {
            meta.put("failedPinAttempts", 0).put("nextPinAttemptAt", 0L)
            writeMeta(meta)
        } catch (error: Exception) {
            logFailure("Pengaturan ulang penghitung PIN", error)
        }
    }

    override fun messageForFailure(operation: String, error: Throwable): String {
        logFailure(operation, error)
        val causes = generateSequence(error) { it.cause }.toList()
        val delay = causes.filterIsInstance<PinAttemptDelay>().firstOrNull()
        val invalid = causes.filterIsInstance<InvalidCredentialException>().firstOrNull()
        val storage = causes.filterIsInstance<java.io.IOException>().firstOrNull()
        val detail = storage?.message.orEmpty().lowercase()
        fun localized(resourceId: Int, vararg arguments: Any): String =
            appContext?.getString(resourceId, *arguments) ?: "Operasi belum berhasil. Detail aman tersedia di log perangkat."
        return when {
            delay != null -> localized(R.string.error_pin_delay, delay.remainingSeconds)
            invalid?.credentialKind == "recovery" -> localized(R.string.error_recovery_mismatch)
            invalid?.credentialKind == "passphrase" -> localized(R.string.error_passphrase_mismatch)
            invalid != null -> localized(R.string.error_pin_mismatch)
            causes.any { it is AEADBadTagException } -> localized(R.string.error_encrypted_data)
            causes.any { it is BackupRejectedException } -> localized(R.string.backup_rejected)
            causes.any { it is SecurityException } -> localized(R.string.error_access_denied, operation.lowercase())
            storage != null && (detail.contains("space") || detail.contains("enospc") || detail.contains("disk full")) ->
                localized(R.string.error_storage_full)
            storage != null -> localized(R.string.error_storage_unavailable)
            causes.any { it is org.json.JSONException } -> localized(R.string.error_vault_metadata)
            else -> localized(R.string.error_generic, operation)
        }
    }

    @Synchronized
    private fun logFailure(operation: String, error: Throwable) {
        val rootCause = generateSequence(error) { it.cause }.last()
        val detail = rootCause.message.orEmpty().replace(Regex("[\\r\\n]+"), " ").take(160)
        val line = System.currentTimeMillis().toString() + " " + operation + " gagal (" +
            rootCause.javaClass.simpleName + ")" + if (detail.isBlank()) "" else ": " + detail
        Log.e("Brankas", line)
        try {
            storage.appendFailure(line)
        } catch (_: Exception) {
            // Logging must never mask the original vault failure.
        }
    }

    private fun saveEntries(master: ByteArray, entries: List<VaultEntry>) {
        indexStore.write(master, entries)
    }

    private fun readMeta(): JSONObject = storage.readMetadata()

    private fun writeMeta(meta: JSONObject) {
        storage.writeMetadata(meta)
    }

    private fun deriveKey(secret: CharArray, salt: ByteArray): ByteArray =
        VaultCrypto.deriveKey(secret, salt)

    private fun encrypt(key: ByteArray, clear: ByteArray): Pair<ByteArray, ByteArray> =
        VaultCrypto.encrypt(key, clear, random)

    private fun decrypt(key: ByteArray, iv: ByteArray, encrypted: ByteArray): ByteArray =
        VaultCrypto.decrypt(key, iv, encrypted)

    private fun decrypt(key: ByteArray, combined: ByteArray): ByteArray =
        VaultCrypto.decryptCombined(key, combined)

    private fun context(): Context = checkNotNull(appContext) { "Konteks Android tidak tersedia." }
}
