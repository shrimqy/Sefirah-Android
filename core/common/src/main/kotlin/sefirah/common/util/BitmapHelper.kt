package sefirah.common.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream


fun bitmapToBase64(bitmap: Bitmap): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
    val byteArray = outputStream.toByteArray()
    return Base64.encodeToString(byteArray, Base64.NO_WRAP)
}

fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable) {
        return drawable.bitmap
    }
    val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

fun drawableToBase64(drawable: Drawable): String? {
    val bitmap =  drawableToBitmap(drawable)
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
    val byteArray = outputStream.toByteArray()
    return Base64.encodeToString(byteArray, Base64.NO_WRAP)
}

fun base64ToBitmap(base64String: String?): Bitmap? {
    return try {
        if (base64String == null) return null
        val decodedBytes = Base64.decode(base64String, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    } catch (e: Exception) {
        Log.e("BitmapHelper", "Error decoding base64 thumbnail", e)
        null
    }
}

fun base64ToIconCompat(base64String: String?): IconCompat? {
    val bitmap = base64ToBitmap(base64String)
    return if (bitmap != null) {
        IconCompat.createWithBitmap(bitmap)
    }
    else {
        null
    }
}

fun drawableToBase64Compressed(
    drawable: Drawable, 
    maxSize: Int = 1024
): String? {
    if (drawable !is BitmapDrawable) return null
    
    val originalBitmap = drawable.bitmap
    val originalSize = originalBitmap.byteCount
    
    // Calculate dynamic quality based on original image size
    // For images > 8MB, use minimum quality (30)
    // For images < 1MB, use maximum quality (90)
    // For images in between, scale quality linearly
    val quality = when {
        originalSize >= 8_000_000 -> 30  // Minimum quality for very large images
        originalSize <= 1_000_000 -> 90  // Maximum quality for small images
        else -> {
            // Linear interpolation between 90 and 30 based on size
            val sizeRatio = (originalSize - 1_000_000) / 7_000_000f
            (90 - (sizeRatio * 60)).toInt()  // 60 is the range between max (90) and min (30)
        }
    }
    
    // Calculate scaling factor to reduce size while maintaining aspect ratio
    val scale = maxSize.toFloat() / maxOf(originalBitmap.width, originalBitmap.height)
    val targetWidth = (originalBitmap.width * scale).toInt()
    val targetHeight = (originalBitmap.height * scale).toInt()
    
    // Create scaled bitmap
    val scaledBitmap = originalBitmap.scale(targetWidth, targetHeight)
    
    // Compress to bytes
    val outputStream = ByteArrayOutputStream()
    scaledBitmap.compress(
        Bitmap.CompressFormat.PNG,
        quality.coerceIn(30, 90),  // Ensure quality stays within reasonable bounds
        outputStream
    )
    
    // Convert to Base64
    val byteArray = outputStream.toByteArray()
    return Base64.encodeToString(byteArray, Base64.NO_WRAP)
}