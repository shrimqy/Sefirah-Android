package sefirah.common.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import java.net.URLDecoder
import java.text.DecimalFormat

//fun extractMetadata(context: Context, uri: Uri): FileMetadata {
//    var fileName = ""
//    var fileSize: Long = 0
//    var fileType = ""
//    var creationDate: String? = null
//    var modificationDate: String? = null
//
//    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
//        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
//        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
//
//        if (cursor.moveToFirst()) {
//            if (nameIndex != -1) {
//                fileName = cursor.getString(nameIndex)
//            }
//            if (sizeIndex != -1) {
//                fileSize = cursor.getLong(sizeIndex)
//            }
//        }
//    }
//
//    // Get file type
//    fileType = context.contentResolver.getType(uri) ?: "application/octet-stream"
//
//    // Try to get creation and modification dates
//    try {
//        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
//        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
//
//        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
//            creationDate = dateFormat.format(Date(pfd.statSize))
//            modificationDate = dateFormat.format(Date(pfd.statSize))
//        }
//    } catch (e: Exception) {
//        Log.e("Metadata", "Error getting file dates", e)
//    }
//
//    return FileMetadata(
//        fileName = fileName,
//        fileSize = fileSize,
//        uri = uri.toString(),
//        mimeType = fileType
//    )
//}





/**
 * Helper function to return a human-readable version of the URI.
 * It supports both document tree URIs (content://...) and direct file paths (/storage/emulated/0/...).
 */
fun getReadablePathFromUri(context: Context, uriString: String): String {
    return if (uriString.startsWith("content://")) {
        // Parse the URI and convert it to a human-readable path
        val uri = Uri.parse(uriString)
        getPathFromTreeUri(uri)
    } else {
        // Return the file path as is (e.g., "/storage/emulated/0/Downloads")
        "/storage/emulated/0/Download"
    }
}

/**
 * Helper function to get the human-readable path from a Document Tree URI.
 */
private fun getPathFromTreeUri(uri: Uri): String {
    // Decode the URI to make it human-readable
    val decodedPath = URLDecoder.decode(uri.toString(), "UTF-8")

    return when {
        decodedPath.contains("primary:") -> {
            // Convert "primary:" to "/storage/emulated/0/" for primary storage
            decodedPath.replaceFirst("content://com.android.externalstorage.documents/tree/primary:", "/storage/emulated/0/")
                .replaceFirst("/document/primary:", "")
        }
        else -> {
            // Fallback if it's not the primary storage
            decodedPath
        }
    }
}