package com.castle.sefirah.util

import android.content.Context
import android.os.Build
import android.os.Environment
import android.widget.Toast
import com.castle.sefirah.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashLogUtil(
    private val context: Context
) {
    suspend fun dumpLogs(exception: Throwable? = null) = withNonCancellableContext {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "sefirah_logs_$timestamp.txt"
            
            // Create file in Downloads folder
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val logFile = File(downloadsDir, fileName)
            
            // Create the file
            logFile.createNewFile()
            
            // Write debug info and exception details
            logFile.appendText(getDebugInfo() + "\n\n")
            exception?.let { logFile.appendText("$it\n\n") }
            
            // Capture logcat output
            Runtime.getRuntime().exec("logcat *:V -d -f ${logFile.absolutePath}").waitFor()
            
            // Show toast notification
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Logs saved to Downloads/$fileName",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Throwable) {
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Failed to save logs: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun getDebugInfo(): String {
        return """
            App version: ${BuildConfig.VERSION_NAME}, ${BuildConfig.VERSION_CODE})
            Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}; build ${Build.DISPLAY})
            Device brand: ${Build.BRAND}
            Device manufacturer: ${Build.MANUFACTURER}
            Device name: ${Build.DEVICE} (${Build.PRODUCT})
            Device model: ${Build.MODEL}
        """.trimIndent()
    }
}

suspend fun <T> withNonCancellableContext(block: suspend CoroutineScope.() -> T) =
    withContext(NonCancellable, block)