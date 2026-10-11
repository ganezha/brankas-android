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

/**
 * One portable file containing the vault's already-encrypted records.
 * The archive does not decrypt photos, videos, or notes. A trailing SHA-256 rejects a truncated copy.
 */
internal object VaultArchive {
    private val magic = "BRANKASBK1".toByteArray(Charsets.US_ASCII)
    private val allowedName = Regex("vault\\.meta|index\\.enc|[A-Za-z0-9-]{1,80}(?:\\.note)?\\.vlt")
    private const val maxFileBytes = 8L * 1024L * 1024L * 1024L
    private const val maxMetadataBytes = 1024L * 1024L

    fun export(vaultDir: File, output: OutputStream) {
        val files = vaultFiles(vaultDir)
        if (files.none { it.name == "vault.meta" } || files.none { it.name == "index.enc" }) {
            throw IllegalStateException("Brankas belum ada.")
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
        data.writeByte(1)
        data.writeShort(files.size)
        val buffer = ByteArray(64 * 1024)
        files.forEach { file ->
            val name = file.name.toByteArray(Charsets.UTF_8)
            data.writeShort(name.size)
            data.write(name)
            data.writeLong(file.length())
            FileInputStream(file).use { input ->
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    data.write(buffer, 0, count)
                }
            }
        }
        data.flush()
        output.write(digest.digest())
        output.flush()
    }

    fun restore(input: InputStream, vaultDir: File) {
        val parent = vaultDir.parentFile ?: throw IOException("Lokasi brankas tidak tersedia.")
        val staging = File(parent, "vault-restore-${UUID.randomUUID()}")
        if (!staging.mkdirs()) throw IOException("Folder pemulihan tidak dapat dibuat.")
        try {
            readStaging(input, staging)
            if (!File(staging, "vault.meta").isFile || !File(staging, "index.enc").isFile) {
                throw BackupRejectedException("Cadangan tidak lengkap.")
            }
            val metadata = File(staging, "vault.meta")
            if (metadata.length() > maxMetadataBytes) throw BackupRejectedException("Cadangan tidak lengkap.")
            JSONObject(metadata.readText())
            swapIntoPlace(staging, vaultDir)
        } catch (error: Exception) {
            staging.deleteRecursively()
            throw error
        }
    }

    private fun vaultFiles(vaultDir: File): List<File> =
        vaultDir.listFiles()
            .orEmpty()
            .filter { it.isFile && allowedName.matches(it.name) }
            .sortedBy { it.name }

    private fun readStaging(input: InputStream, staging: File) {
        val source = HoldbackStream(input)
        val header = source.readExact(magic.size + 1 + 2)
        if (!header.copyOf(magic.size).contentEquals(magic) || header[magic.size].toInt() != 1) {
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
    }

    private fun swapIntoPlace(staging: File, vaultDir: File) {
        val previous = File(vaultDir.parentFile, "vault-previous-${UUID.randomUUID()}")
        val hadVault = vaultDir.exists()
        if (hadVault && !vaultDir.renameTo(previous)) throw IOException("Brankas yang ada tidak dapat disingkirkan.")
        if (!staging.renameTo(vaultDir)) {
            if (hadVault) previous.renameTo(vaultDir)
            throw IOException("Cadangan tidak dapat dipasang.")
        }
        if (hadVault) previous.deleteRecursively()
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
