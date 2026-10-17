package id.rumahbrankas.app

import java.io.File
import javax.crypto.Cipher

/** A short-lived Argon2id-wrapped master key prepared before the user picks a backup location. */
class BackupKeyEnvelope internal constructor(
    internal val salt: ByteArray,
    internal val iv: ByteArray,
    internal val wrappedKey: ByteArray,
) {
    internal fun clear() {
        salt.fill(0)
        iv.fill(0)
        wrappedKey.fill(0)
    }
}

/** Staged, authenticated backup data that is not installed until a new PIN is provided. */
class BackupRestoreSession internal constructor(
    internal val ownerDirectory: String,
    internal val stagingDirectory: File,
    internal val recoveryCode: CharArray,
    internal val archiveVersion: Int,
) {
    internal val lock = Any()
    internal var active: Boolean = true

    internal fun invalidate() = synchronized(lock) {
        active = false
        recoveryCode.fill('\u0000')
    }
}

/** Operations exposed to the presentation layer; implementation and persistence stay replaceable. */
interface VaultRepository {
    fun exists(): Boolean
    fun create(secret: CharArray, credentialKind: String): CreatedVault
    fun commitCreate(created: CreatedVault)
    fun credentialType(): String
    fun unlock(secret: CharArray): ByteArray
    fun unlockWithRecovery(code: CharArray): ByteArray
    fun resetCredential(master: ByteArray, newSecret: CharArray, credentialKind: String)
    fun entries(master: ByteArray): List<VaultEntry>
    fun readNote(master: ByteArray, entry: VaultEntry): String
    fun addNote(master: ByteArray, title: String, text: String): VaultEntry
    fun importAndMove(master: ByteArray, uri: String): ImportOutcome
    fun restoreAndMove(master: ByteArray, entry: VaultEntry, destination: String): Boolean
    fun decryptToCache(master: ByteArray, entry: VaultEntry): File
    fun clearPreviewCache(): Boolean
    fun prepareBackupExport(master: ByteArray, recoveryCode: CharArray): BackupKeyEnvelope
    fun exportBackup(destination: String, envelope: BackupKeyEnvelope)
    fun prepareBackupRestore(source: String, recoveryCode: CharArray): BackupRestoreSession
    fun completeBackupRestore(session: BackupRestoreSession, newPin: CharArray)
    fun cancelBackupRestore(session: BackupRestoreSession)
    fun delete(master: ByteArray, entry: VaultEntry)
    fun hasBiometric(): Boolean
    fun newBiometricEncryptCipher(): Cipher
    fun saveBiometricWrap(cipher: Cipher, master: ByteArray)
    fun biometricDecryptCipher(): Cipher
    fun finishBiometricUnlock(cipher: Cipher): ByteArray
    fun messageForFailure(operation: String, error: Throwable): String
}
