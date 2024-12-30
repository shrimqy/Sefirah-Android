package sefirah.network.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import sefirah.domain.model.FileMetadata
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun getFileMetadata(context: Context, uri: Uri): FileMetadata {
    var fileName = ""
    var fileSize: Long = 0
    var fileType = ""
    var creationDate: String? = null
    var modificationDate: String? = null

    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

        if (cursor.moveToFirst()) {
            if (nameIndex != -1) {
                fileName = cursor.getString(nameIndex)
            }
            if (sizeIndex != -1) {
                fileSize = cursor.getLong(sizeIndex)
            }
        }
    }

    // Get file type
    fileType = context.contentResolver.getType(uri) ?: "application/octet-stream"

    // Try to get creation and modification dates
    try {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")

        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            creationDate = dateFormat.format(Date(pfd.statSize))
            modificationDate = dateFormat.format(Date(pfd.statSize))
        }
    } catch (e: Exception) {
        Log.e("Metadata", "Error getting file dates", e)
    }

    return FileMetadata(
        fileName = fileName,
        fileSize = fileSize,
        uri = uri.toString(),
        mimeType = fileType
    )
}