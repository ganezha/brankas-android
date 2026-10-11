package id.rumahbrankas.app

import java.io.File
import javax.crypto.Cipher

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
    fun exportBackup(destination: String)
    fun restoreBackup(source: String)
    fun delete(master: ByteArray, entry: VaultEntry)
    fun hasBiometric(): Boolean
    fun newBiometricEncryptCipher(): Cipher
    fun saveBiometricWrap(cipher: Cipher, master: ByteArray)
    fun biometricDecryptCipher(): Cipher
    fun finishBiometricUnlock(cipher: Cipher): ByteArray
    fun messageForFailure(operation: String, error: Throwable): String
}
