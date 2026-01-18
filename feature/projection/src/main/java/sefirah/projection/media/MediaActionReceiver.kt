package sefirah.projection.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import sefirah.domain.interfaces.NetworkManager
import sefirah.domain.model.PlaybackAction
import sefirah.domain.model.PlaybackActionType
import javax.inject.Inject

@AndroidEntryPoint
class MediaActionReceiver : BroadcastReceiver() {
    @Inject lateinit var networkManager: NetworkManager
    
    override fun onReceive(context: Context, intent: Intent) {
        val source = intent.getStringExtra(EXTRA_SOURCE) ?: return
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: return
        val action = intent.action ?: return
        
        val actionType = when (action) {
            ACTION_PLAY -> PlaybackActionType.Play
            ACTION_PAUSE -> PlaybackActionType.Pause
            ACTION_NEXT -> PlaybackActionType.Next
            ACTION_PREVIOUS -> PlaybackActionType.Previous
            else -> return
        }

        val session = PlaybackAction(
            source = source,
            playbackActionType = actionType
        )

        networkManager.sendMessage(deviceId, session)
    }

    companion object {
        const val ACTION_PLAY: String = "ACTION_PLAY"
        const val ACTION_PAUSE: String = "ACTION_PAUSE"
        const val ACTION_PREVIOUS: String = "ACTION_PREVIOUS"
        const val ACTION_NEXT: String = "ACTION_NEXT"
        const val EXTRA_SOURCE: String = "source"
        const val EXTRA_DEVICE_ID: String = "deviceId"
    }
}