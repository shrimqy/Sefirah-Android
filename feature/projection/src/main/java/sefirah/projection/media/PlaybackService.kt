package sefirah.projection.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import sefirah.domain.interfaces.DeviceManager
import sefirah.domain.interfaces.NetworkManager
import sefirah.domain.interfaces.NotificationCallback
import sefirah.domain.interfaces.PreferencesRepository
import sefirah.domain.model.MediaAction
import sefirah.domain.model.MediaActionType
import sefirah.domain.model.PlaybackInfo
import sefirah.domain.model.PlaybackInfoType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackService @Inject constructor(
    private val context: Context,
    private val networkManager: NetworkManager,
    private val deviceManager: DeviceManager,
    private val preferencesRepository: PreferencesRepository
) : NotificationCallback {
    private val tag = "MediaSessionTracker"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var mediaSessionManager: MediaSessionManager? = null
    private var sessionChangeListener: MediaSessionChangeListener? = null

    private val mediaSessions = mutableMapOf<String, MediaSession>()
    private val sessionCallbacks = mutableMapOf<String, MediaSessionCallback>()

    private val deviceIds = MutableStateFlow<Set<String>>(emptySet())

    @Volatile
    private var notificationListenerConnected = false

    private val handler = Handler(Looper.getMainLooper())

    init {
        scope.launch {
            deviceManager.pairedDevices.collect { pairedDevices ->
                val connectedDeviceIds = pairedDevices
                    .filter { it.connectionState.isConnected }
                    .map { it.deviceId }
                    .toSet()
                deviceIds.value = connectedDeviceIds
                updateActivation()
            }
        }
    }

    private fun updateActivation() {
        scope.launch {
            val shouldBeActive = notificationListenerConnected &&
                hasAnyConnectedDeviceWithMediaControl()
            when {
                shouldBeActive && mediaSessionManager == null -> initializeManager()
                !shouldBeActive && mediaSessionManager != null -> release()
            }
        }
    }

    private suspend fun hasAnyConnectedDeviceWithMediaControl(): Boolean {
        if (deviceIds.value.isEmpty()) return false
        return deviceIds.value.any { deviceId ->
            preferencesRepository.readMediaPlayerControlSettingsForDevice(deviceId).first()
        }
    }

    private fun initializeManager() {
        try {
            val manager = ContextCompat.getSystemService(context, MediaSessionManager::class.java) ?: return

            val notificationListener = ComponentName(context, sefirah.notification.NotificationListener::class.java)
            mediaSessionManager = manager
            sessionChangeListener = MediaSessionChangeListener()
            manager.addOnActiveSessionsChangedListener(sessionChangeListener!!, notificationListener, handler)

            val controllers = manager.getActiveSessions(notificationListener)
            createSessions(controllers)
            Log.d(tag, "MediaSessionTracker initialized in init with ${mediaSessions.size} sessions")
        } catch (e: Exception) {
            // Permission not granted or service not connected yet - will initialize in onListenerConnected
            Log.d(tag, "Could not initialize in init, will wait for onListenerConnected: ${e.message}")
        }
    }
    
    override fun onListenerConnected(service: NotificationListenerService) {
        notificationListenerConnected = true
        updateActivation()
    }

    override fun onListenerDisconnected() {
        notificationListenerConnected = false
        release()
    }
    
    override fun onNotificationPosted(notification: StatusBarNotification) { }
    
    override fun onNotificationRemoved(notification: StatusBarNotification) { }

    fun release() {
        mediaSessionManager?.let { manager ->
            sessionChangeListener?.let { listener ->
                manager.removeOnActiveSessionsChangedListener(listener)
            }
        }

        mediaSessions.keys.toList().forEach { packageName ->
            sessionCallbacks[packageName]?.let { cb ->
                mediaSessions[packageName]?.controller?.unregisterCallback(cb)
            }
        }
        sessionCallbacks.clear()
        mediaSessions.clear()
        sessionChangeListener = null
        mediaSessionManager = null
    }

    private fun createSessions(controllers: List<MediaController>?) {
        if (controllers == null) return

        mediaSessions.keys.toList().forEach { packageName ->
            sessionCallbacks[packageName]?.let { cb ->
                mediaSessions[packageName]?.controller?.unregisterCallback(cb)
            }
        }
        sessionCallbacks.clear()
        mediaSessions.clear()

        controllers.forEach { controller ->
            if (controller.packageName == context.packageName) return@forEach
            try {
                val pkg = controller.packageName
                val appName = getAppName(pkg)
                val player = MediaSession(controller, appName)
                val callback = MediaSessionCallback(this, pkg)
                controller.registerCallback(callback, handler)
                mediaSessions[pkg] = player
                sessionCallbacks[pkg] = callback
            } catch (e: Exception) {
                Log.e(tag, "Failed to create session for ${controller.packageName}", e)
            }
        }
    }

    fun onSessionMetadataChanged(packageName: String) {
        val session = mediaSessions[packageName] ?: return
        sendToDevices(session.toPlaybackSession())
    }

    fun onSessionPlaybackStateChanged(packageName: String) {
        val session = mediaSessions[packageName] ?: return
        val message = PlaybackInfo(
            infoType = PlaybackInfoType.PlaybackUpdate,
            source = packageName,
            isPlaying = session.isPlaying(),
            position = session.getPosition().toDouble()
        )
        sendToDevices(message)
    }

    private fun getAppName(packageName: String): String {
        return try {
            val packageManager = context.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: Exception) {
            Log.w(tag, "Failed to get app name for $packageName", e)
            packageName
        }
    }

    fun getActivePackageNames(): Set<String> = mediaSessions.keys
    
    /**
     * Handle incoming PlaybackAction from connected devices to control
     * Android media sessions. Delegates to the matching [MediaSession].
     */
    fun handlePlaybackAction(action: MediaAction) {
        val packageName = action.source
        val player = mediaSessions[packageName] ?: return

        try {
            when (action.actionType) {
                MediaActionType.Play -> if (player.canPlay()) player.play()
                MediaActionType.Pause -> if (player.canPause()) player.pause()
                MediaActionType.Next -> if (player.canGoNext()) player.next()
                MediaActionType.Previous -> if (player.canGoPrevious()) player.previous()
                MediaActionType.Seek -> if (player.canSeek() && action.value != null) {
                    player.setPosition(action.value!!.toLong())
                }
                MediaActionType.VolumeUpdate -> if (action.value != null) {
                    val volumePercent = (action.value!!).toInt().coerceIn(0, 100)
                    player.setVolume(volumePercent)
                    onSessionPlaybackStateChanged(packageName)
                }
                else -> Log.d(tag, "Action ${action.actionType} not supported for Android sessions")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to handle playback action for $packageName", e)
        }
    }

    fun sendActiveSessions(deviceId: String) {
        scope.launch {
            if (!preferencesRepository.readMediaPlayerControlSettingsForDevice(deviceId).first()) return@launch
            mediaSessions.values.forEach { session ->
                val msg = session.toPlaybackSession()
                networkManager.sendMessage(deviceId, msg)
            }
        }
    }
    
    private fun sendToDevices(session: PlaybackInfo) {
        val allDeviceIds = deviceIds.value
        if (allDeviceIds.isEmpty()) return
        scope.launch {
            val allowed = allDeviceIds.filter { deviceId ->
                preferencesRepository.readMediaPlayerControlSettingsForDevice(deviceId).first()
            }
            allowed.forEach { deviceId ->
                networkManager.sendMessage(deviceId, session)
            }
        }
    }

    private inner class MediaSessionChangeListener : MediaSessionManager.OnActiveSessionsChangedListener {
        override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
            controllers?.let {
                createSessions(controllers)
            }
        }
    }

    class MediaSessionCallback(val service: PlaybackService, val packageName: String) : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            service.onSessionMetadataChanged(packageName)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            service.onSessionPlaybackStateChanged(packageName)
        }
    }
}