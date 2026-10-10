package id.rumahbrankas.app

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.InputStream
import java.io.OutputStream

/** Android SAF adapter; the transaction logic itself stays testable without Android. */
internal class ContentResolverDocumentAccess(
    private val context: Context,
    private val resolver: ContentResolver,
) : VaultDocumentAccess {
    override fun displayName(reference: String): String? {
        val uri = Uri.parse(reference)
        return resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    override fun mimeType(reference: String): String? = resolver.getType(Uri.parse(reference))

    override fun openInputStream(reference: String): InputStream? =
        resolver.openInputStream(Uri.parse(reference))

    override fun openOutputStream(reference: String, mode: String): OutputStream? =
        resolver.openOutputStream(Uri.parse(reference), mode)

    override fun delete(reference: String): Boolean {
        val uri = Uri.parse(reference)
        return if (DocumentsContract.isDocumentUri(context, uri)) {
            DocumentsContract.deleteDocument(resolver, uri)
        } else {
            resolver.delete(uri, null, null) > 0
        }
    }
}
