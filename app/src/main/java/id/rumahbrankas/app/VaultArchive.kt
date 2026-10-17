package id.rumahbrankas.app

import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONObject

/** The archive is not a Brankas backup, or its contents failed the integrity check. */
internal class BackupRejectedException(message: String) : Exception(message)

internal data class StagedVaultArchive(
    val directory: File,
    val version: Int,
    val metadata: JSONObject,
)

/**
 * One portable file containing the vault's already-encrypted records.
 * The archive does not decrypt photos, videos, or notes. A trailing SHA-256 rejects a truncated copy.
 */
internal object VaultArchive {
    private val magic = "BRANKASBK1".toByteArray(Charsets.US_ASCII)
    private val allowedName = Regex("vault\\.meta|index\\.enc|[A-Za-z0-9-]{1,80}(?:\\.note)?\\.vlt")
    private const val maxFileBytes = 8L * 1024L * 1024L * 1024L
    private const val maxMetadataBytes = 1024L * 1024L

    fun export(vaultDir: File, output: OutputStream, backupMetadata: JSONObject) {
        val files = vaultFiles(vaultDir)
        if (files.none { it.name == "vault.meta" } || files.none { it.name == "index.enc" }) {
            throw IllegalStateException("Brankas belum ada.")
        }
        if (listOf(
                "pinSalt", "pinIv", "pinKey",
                "recoverySalt", "recoveryIv", "recoveryKey",
                "bioIv", "bioKey",
            ).any { backupMetadata.has(it) }
        ) {
            throw IllegalArgumentException("Metadata cadangan masih berisi bungkus kredensial perangkat.")
        }
        if (backupMetadata.optString("backupKdf") != BackupKeyKdf.ID ||
            !backupMetadata.has("backupSalt") || !backupMetadata.has("backupIv") || !backupMetadata.has("backupKey")
        ) {
            throw IllegalArgumentException("Kunci cadangan tidak tersedia.")
        }
        if (backupMetadata.toString().toByteArray(Charsets.UTF_8).size.toLong() > maxMetadataBytes) {
            throw IllegalArgumentException("Metadata cadangan terlalu besar.")
        }
        if (files.size > 65_535) throw IllegalStateException("Jumlah berkas cadangan terlalu besar.")
        val digest = MessageDigest.getInstance("SHA-256")
        val hashed = object : OutputStream() {
            override fun write(value: Int) {
                output.write(value)
                digest.update(value.toByte())
            }

            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                output.write(bytes, offset, length)
                digest.update(bytes, offset, length)
            }
        }
        val data = DataOutputStream(hashed)
        data.write(magic)
        data.writeByte(2)
        data.writeShort(files.size)
        val buffer = ByteArray(64 * 1024)
        files.forEach { file ->
            val name = file.name.toByteArray(Charsets.UTF_8)
            val metadataBytes = if (file.name == "vault.meta") backupMetadata.toString().toByteArray(Charsets.UTF_8) else null
            val fileSize = metadataBytes?.size?.toLong() ?: file.length()
            data.writeShort(name.size)
            data.write(name)
            data.writeLong(fileSize)
            if (metadataBytes != null) {
                data.write(metadataBytes)
            } else {
                FileInputStream(file).use { input ->
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        data.write(buffer, 0, count)
                    }
                }
            }
        }
        data.flush()
        output.write(digest.digest())
        output.flush()
    }

    fun stage(input: InputStream, vaultDir: File): StagedVaultArchive {
        val parent = vaultDir.parentFile ?: throw IOException("Lokasi brankas tidak tersedia.")
        val staging = File(parent, "vault-restore-${UUID.randomUUID()}")
        if (!staging.mkdirs()) throw IOException("Folder pemulihan tidak dapat dibuat.")
        try {
            val version = readStaging(input, staging)
            if (!File(staging, "vault.meta").isFile || !File(staging, "index.enc").isFile) {
                throw BackupRejectedException("Cadangan tidak lengkap.")
            }
            val metadata = File(staging, "vault.meta")
            if (metadata.length() > maxMetadataBytes) throw BackupRejectedException("Cadangan tidak lengkap.")
            val parsedMetadata = JSONObject(metadata.readText())
            if (version >= 2 && listOf(
                    "pinSalt", "pinIv", "pinKey",
                    "recoverySalt", "recoveryIv", "recoveryKey",
                "bioIv", "bioKey",
                ).any { parsedMetadata.has(it) }
            ) {
                throw BackupRejectedException("Cadangan versi baru berisi bungkus kunci yang tidak diizinkan.")
            }
            return StagedVaultArchive(staging, version, parsedMetadata)
        } catch (error: Exception) {
            staging.deleteRecursively()
            throw error
        }
    }

    fun writeMetadata(staged: StagedVaultArchive, metadata: JSONObject) {
        val bytes = metadata.toString().toByteArray(Charsets.UTF_8)
        if (bytes.size.toLong() > maxMetadataBytes) throw IOException("Metadata cadangan terlalu besar.")
        FileOutputStream(File(staged.directory, "vault.meta")).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
    }

    fun install(
        staged: StagedVaultArchive,
        vaultDir: File,
        rename: (File, File) -> Boolean = { source, target -> source.renameTo(target) },
        verifyInstalled: (File) -> Unit = {},
    ) {
        val parent = vaultDir.parentFile ?: throw IOException("Lokasi brankas tidak tersedia.")
        val previous = File(parent, "vault-previous-${UUID.randomUUID()}")
        val hadVault = vaultDir.exists()
        if (hadVault && !rename(vaultDir, previous)) {
            throw IOException("Brankas yang ada tidak dapat diamankan sementara.")
        }

        var installed = false
        try {
            if (!rename(staged.directory, vaultDir)) throw IOException("Cadangan tidak dapat dipasang.")
            installed = true
            verifyInstalled(vaultDir)
        } catch (error: Throwable) {
            val rollbackErrors = mutableListOf<Throwable>()
            if (installed && vaultDir.exists()) {
                val stagedTarget = if (!staged.directory.exists()) {
                    staged.directory
                } else {
                    File(parent, "vault-restore-failed-${UUID.randomUUID()}")
                }
                try {
                    val moved = rename(vaultDir, stagedTarget) ||
                        (stagedTarget != staged.directory && rename(vaultDir, File(parent, "vault-restore-failed-${UUID.randomUUID()}")))
                    if (!moved) {
                        throw IOException("Cadangan gagal yang baru dipasang tidak dapat dipindahkan.")
                    }
                } catch (rollback: Throwable) {
                    rollbackErrors += rollback
                }
            }
            if (hadVault && previous.exists() && !vaultDir.exists()) {
                try {
                    if (!rename(previous, vaultDir)) {
                        throw IOException("Brankas lama tetap tersimpan di ${previous.name}, tetapi belum dapat dipasang kembali.")
                    }
                } catch (rollback: Throwable) {
                    rollbackErrors += rollback
                }
            }
            rollbackErrors.forEach(error::addSuppressed)
            throw error
        }

        // The old vault is removed only after the installed copy passes its open and integrity checks.
        if (hadVault) previous.deleteRecursively()
    }

    fun discard(staged: StagedVaultArchive) {
        staged.directory.deleteRecursively()
    }

    private fun vaultFiles(vaultDir: File): List<File> =
        vaultDir.listFiles()
            .orEmpty()
            .filter { it.isFile && allowedName.matches(it.name) }
            .sortedBy { it.name }

    private fun readStaging(input: InputStream, staging: File): Int {
        val source = HoldbackStream(input)
        val header = source.readExact(magic.size + 1 + 2)
        val version = header[magic.size].toInt() and 0xFF
        if (!header.copyOf(magic.size).contentEquals(magic) || version !in 1..2) {
            throw BackupRejectedException("Berkas ini bukan cadangan Brankas.")
        }
        val count = ((header[magic.size + 1].toInt() and 0xFF) shl 8) or (header[magic.size + 2].toInt() and 0xFF)
        val seen = HashSet<String>(count)
        repeat(count) {
            val nameLengthBytes = source.readExact(2)
            val nameLength = ((nameLengthBytes[0].toInt() and 0xFF) shl 8) or (nameLengthBytes[1].toInt() and 0xFF)
            if (nameLength !in 1..120) throw BackupRejectedException("Nama berkas cadangan tidak valid.")
            val name = source.readExact(nameLength).toString(Charsets.UTF_8)
            if (!allowedName.matches(name) || !seen.add(name)) throw BackupRejectedException("Isi cadangan tidak valid.")
            val sizeBytes = source.readExact(8)
            val size = sizeBytes.fold(0L) { total, byte -> (total shl 8) or (byte.toLong() and 0xFF) }
            if (size < 0L || size > maxFileBytes) throw BackupRejectedException("Ukuran berkas cadangan tidak valid.")
            val target = File(staging, name)
            FileOutputStream(target).use { output ->
                var remaining = size
                val buffer = ByteArray(64 * 1024)
                while (remaining > 0L) {
                    val countRead = source.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (countRead < 0) throw BackupRejectedException("Cadangan terpotong.")
                    output.write(buffer, 0, countRead)
                    remaining -= countRead
                }
                output.fd.sync()
            }
        }
        if (source.read(ByteArray(1), 0, 1) >= 0) throw BackupRejectedException("Cadangan memiliki data tambahan.")
        if (!MessageDigest.isEqual(source.digest(), source.heldHash())) {
            throw BackupRejectedException("Cadangan rusak atau tidak lengkap.")
        }
        return version
    }

}

/** Reads an archive while keeping its final 32 bytes out of the hashed payload. */
private class HoldbackStream(private val input: InputStream) {
    private val digest = MessageDigest.getInstance("SHA-256")
    private var held = ByteArray(0)
    private var finished = false

    fun readExact(length: Int): ByteArray {
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = read(bytes, offset, length - offset)
            if (count < 0) throw BackupRejectedException("Cadangan terpotong.")
            offset += count
        }
        return bytes
    }

    fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (finished) return -1
        if (length == 0) return 0
        var written = 0
        while (written < length) {
            val available = held.size - hashLength
            if (available > 0) {
                val copying = minOf(available, length - written)
                held.copyInto(buffer, offset + written, 0, copying)
                digest.update(buffer, offset + written, copying)
                held = held.copyOfRange(copying, held.size)
                written += copying
                continue
            }
            val incoming = ByteArray(length - written)
            val count = input.read(incoming)
            if (count == 0) throw IOException("Cadangan tidak dapat dibaca.")
            if (count < 0) {
                finished = true
                if (held.size != hashLength) throw BackupRejectedException("Cadangan terpotong.")
                return if (written == 0) -1 else written
            }
            held += incoming.copyOf(count)
        }
        return written
    }

    fun digest(): ByteArray = digest.digest()

    fun heldHash(): ByteArray {
        if (held.size != hashLength) throw BackupRejectedException("Cadangan terpotong.")
        return held.copyOf()
    }

    private companion object {
        const val hashLength = 32
    }
}
