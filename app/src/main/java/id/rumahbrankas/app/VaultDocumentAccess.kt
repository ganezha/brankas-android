package id.rumahbrankas.app

import java.io.InputStream
import java.io.OutputStream

/** Minimal document I/O boundary so import/restore transactions can be tested on the JVM. */
internal interface VaultDocumentAccess {
    fun displayName(reference: String): String?
    fun mimeType(reference: String): String?
    fun openInputStream(reference: String): InputStream?
    fun openOutputStream(reference: String, mode: String): OutputStream?
    fun delete(reference: String): Boolean
}
