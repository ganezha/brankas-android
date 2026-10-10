package id.rumahbrankas.app

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Writes a sibling .next file, syncs it, then atomically replaces the committed file.
 * Staging and replacement are injectable to exercise interrupted-write behavior on the JVM.
 */
internal class AtomicFileWriter(
    private val writeStaged: (File, ByteArray) -> Unit = { staged, bytes ->
        FileOutputStream(staged).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
    },
    private val replace: (File, File) -> Unit = { staged, target ->
        try {
            Files.move(
                staged.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(staged.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    },
) {
    fun write(target: File, bytes: ByteArray) {
        target.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) {
                throw IllegalStateException("Direktori penyimpanan tidak dapat dibuat.")
            }
        }
        val staged = File(target.parentFile, target.name + ".next")
        try {
            writeStaged(staged, bytes)
            replace(staged, target)
        } finally {
            staged.delete()
        }
    }
}
