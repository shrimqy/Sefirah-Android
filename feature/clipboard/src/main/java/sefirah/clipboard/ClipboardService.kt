package sefirah.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import sefirah.domain.model.ClipboardMessage
import sefirah.domain.model.ClipboardType
import sefirah.domain.repository.NetworkManager
import javax.inject.Inject

class ClipboardService @Inject constructor(
    private val context: Context,
) : ClipboardHandler {

    override fun start() {
        // Start the service
        val intent = Intent(context, ClipboardListener::class.java)
        context.startService(intent)
    }

    override fun stop() {
        // Stop the service
        val intent = Intent(context, ClipboardListener::class.java)
        context.stopService(intent)
    }

    override fun setClipboard(clipboard: ClipboardMessage) {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = if (clipboard.clipboardType == "text/plain") {
            ClipData.newPlainText("Received clipboard", clipboard.content)
        } else {
            val imageBytes = android.util.Base64.decode(clipboard.content, android.util.Base64.DEFAULT)
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

            val extension = clipboard.clipboardType.split("/")[1]
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "clipboard_image_${System.currentTimeMillis()}.$extension")
                put(MediaStore.MediaColumns.MIME_TYPE, clipboard.clipboardType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Clipboard")
            }
            
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )?.also { uri ->
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val compressFormat = when (clipboard.clipboardType) {
                        "image/jpeg" -> android.graphics.Bitmap.CompressFormat.JPEG
                        "image/webp" -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                android.graphics.Bitmap.CompressFormat.WEBP_LOSSLESS
                            } else {
                                android.graphics.Bitmap.CompressFormat.WEBP
                            }
                        }
                        "image/png" -> android.graphics.Bitmap.CompressFormat.PNG
                        else -> android.graphics.Bitmap.CompressFormat.PNG
                    }
                    bitmap.compress(compressFormat, 100, outputStream)
                }
            }
            
            requireNotNull(uri) { "Failed to create image URI" }
            ClipData.newUri(context.contentResolver, "Received image", uri)
        }
        clipboardManager.setPrimaryClip(clip)
    }

}