package sefirah.communication.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/** Completes tempId -> Telephony _id mapping when SmsManager reports a sent provider uri. */
class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tempId = intent.getLongExtra(SmsMmsUtils.EXTRA_TEMP_ID, 0L)
        if (tempId == 0L) return

        val extras = intent.extras
        val uri = extras?.getString("uri")?.let(Uri::parse)
            ?: extras?.let { bundle ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    bundle.getParcelable("uri", Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    bundle.getParcelable("uri")
                }
            }
            ?: intent.data
        val uniqueId = uri?.let { runCatching { ContentUris.parseId(it) }.getOrNull() }

        // Multipart: only the part that wrote Telephony includes uri — ignore OK without id.
        when (resultCode) {
            Activity.RESULT_OK if uniqueId != null && uniqueId >= 0 ->
                SmsMmsUtils.completeTempIdMap(tempId, uniqueId)

            Activity.RESULT_OK -> Unit
            else -> SmsMmsUtils.failTempIdMap()
        }
    }

    companion object {
        const val ACTION = "sefirah.communication.sms.SMS_SENT"
    }
}