package sefirah.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BootReceiver that starts NetworkService on device boot or app update.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                startNetworkService(context)
            }
        }
    }

    private fun startNetworkService(context: Context) {
        NetworkService.start(context)
    }
}