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

    return FileMetadata(
        fileName = fileName,
        fileSize = fileSize,
        mimeType = fileType
    )
}