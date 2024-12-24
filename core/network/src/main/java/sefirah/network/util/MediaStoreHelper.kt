package sefirah.network.util

import android.content.Context
import android.content.Intent
import android.net.Uri

object MediaStoreHelper {
    // Maybe this class could batch successive calls together
    @JvmStatic
    fun indexFile(context: Context, path: Uri?) {
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        mediaScanIntent.setData(path)
        context.sendBroadcast(mediaScanIntent)
    }
}
