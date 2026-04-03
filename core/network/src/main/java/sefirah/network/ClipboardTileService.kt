package sefirah.network

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.core.service.quicksettings.PendingIntentActivityWrapper
import androidx.core.service.quicksettings.TileServiceCompat
import sefirah.clipboard.ClipboardChangeActivity

@RequiresApi(Build.VERSION_CODES.N)
class ClipboardTileService : TileService() {
    override fun onClick() {
        super.onClick()
        TileServiceCompat.startActivityAndCollapse(this, PendingIntentActivityWrapper(
            this,0, Intent(this, ClipboardChangeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        }, PendingIntent.FLAG_ONE_SHOT, true))
    }
}
