package sefirah.common.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.net.URLDecoder
import androidx.core.net.toUri

/**
 * Creates a temporary file in the app's cache directory.
 * The file is marked for deletion on JVM exit.
 * 
 * @param context Application context
 * @param prefix Prefix for the temp file name
 * @param extension File extension (without dot), e.g. "jpg", "png"
 * @return URI to the created temp file via FileProvider
 */
fun createTempFileUri(context: Context, prefix: String, extension: String): Uri {
    val tempFile = createTempFile(context, prefix, extension)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
}

/**
 * Creates a temporary file in the app's cache directory.
 * Use this when you need to write to the file before getting a URI.
 * Call [getFileProviderUri] after writing to get the shareable URI.
 * 
 * @return The created temp File
 */
fun createTempFile(context: Context, prefix: String, extension: String): File {
    return File.createTempFile(
        prefix,
        if (extension.isNotEmpty()) ".$extension" else "",
        context.cacheDir
    ).apply { deleteOnExit() }
}

/**
 * Gets a FileProvider URI for an existing file.
 */
fun getFileProviderUri(context: Context, file: File): Uri {
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/**
 * Helper function to return a human-readable version of the URI.
 * It supports both document tree URIs (content://...) and direct file paths (/storage/emulated/0/...).
 */
fun getReadablePathFromUri(context: Context, uriString: String): String {
    return if (uriString.startsWith("content://")) {
        // Parse the URI and convert it to a human-readable path
        val uri = uriString.toUri()
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