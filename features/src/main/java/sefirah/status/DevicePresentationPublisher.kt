package sefirah.status

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import sefirah.domain.interfaces.DeviceManager
import sefirah.domain.interfaces.NetworkManager
import sefirah.domain.interfaces.PreferencesRepository
import sefirah.domain.model.DeviceInfo
import sefirah.domain.model.DevicePresentation
import sefirah.domain.model.PairedDevice
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DevicePresentationPublisher @Inject constructor(
    private val context: Context,
    private val preferences: PreferencesRepository,
    private val devices: DeviceManager,
    private val network: NetworkManager,
) {
    suspend fun localSettings(): DevicePresentation {
        val saved = runCatching { Json.decodeFromString<DevicePresentation>(
            preferences.readPresentation("local").first()) }.getOrNull()
        if (saved?.isValid() == true) return saved
        val tablet = context.resources.configuration.smallestScreenWidthDp >= 600
        return DevicePresentation(kind = if (tablet) "tablet" else "phone",
            orientation = if (tablet) "landscape" else "portrait", aspectRatio = if (tablet) "4:3" else "9:20")
    }

    suspend fun metadata(peerId: String?, includeImage: Boolean = false): DevicePresentation {
        val settings = localSettings().copy(wallpaperSource = "none", wallpaperId = null, wallpaper = null)
        if (preferences.readPresentation("share").first() != "true" ||
            peerId != null && preferences.readPresentation("allow_$peerId").first() != "true") return settings
        val selected = File(context.filesDir, "device-preview.jpg")
        val bitmap = if (selected.exists()) BitmapFactory.decodeFile(selected.path) else try {
            val drawable = WallpaperManager.getInstance(context).drawable ?: return settings
            if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) return settings
            val scale = minOf(1f, 384f / maxOf(drawable.intrinsicWidth, drawable.intrinsicHeight))
            Bitmap.createBitmap(maxOf(1, (drawable.intrinsicWidth * scale).toInt()),
                maxOf(1, (drawable.intrinsicHeight * scale).toInt()), Bitmap.Config.ARGB_8888).also {
                drawable.setBounds(0, 0, it.width, it.height)
                drawable.draw(Canvas(it))
            }
        } catch (_: SecurityException) { null }
        if (bitmap == null) return settings
        val bytes = ByteArrayOutputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.JPEG, 75, output); output.toByteArray() }
        bitmap.recycle()
        if (bytes.size > 65536) return settings
        val id = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return settings.copy(wallpaperSource = if (selected.exists()) "userSelected" else "wallpaper",
            wallpaperId = id, wallpaper = if (includeImage) Base64.encodeToString(bytes, Base64.NO_WRAP) else null)
    }

    suspend fun publishAll() {
        devices.pairedDevices.value.filter { it.connectionState.isConnected && it.presentation?.version == 1 }.forEach { peer ->
            network.sendMessage(peer.deviceId, DeviceInfo(devices.localDevice.deviceName, presentation = metadata(peer.deviceId)))
        }
    }

    suspend fun receive(peer: PairedDevice, info: DeviceInfo): PairedDevice {
        info.requestedWallpaperId?.let { id ->
            if (peer.presentation?.version == 1 && id.length == 64) {
                val current = metadata(peer.deviceId, true)
                if (current.wallpaperId == id)
                    network.sendMessage(peer.deviceId, DeviceInfo(devices.localDevice.deviceName, presentation = current))
            }
        }
        val incoming = info.presentation ?: return peer.copy(avatar = if (peer.presentation == null) null else peer.avatar)
        if (!incoming.isValid()) return peer
        var image = if (incoming.wallpaperId == peer.presentation?.wallpaperId) peer.avatar else null
        if (incoming.wallpaperSource == "none") image = null
        else incoming.wallpaper?.let { encoded -> image = sefirah.domain.util.PresentationImage.validate(encoded, incoming.wallpaperId) }
        preferences.savePresentation("peer_${peer.deviceId}", Json.encodeToString(DevicePresentation.serializer(), incoming.metadata()))
        if (image == null && incoming.wallpaperSource != "none" && incoming.wallpaper == null)
            network.sendMessage(peer.deviceId, DeviceInfo(devices.localDevice.deviceName, requestedWallpaperId = incoming.wallpaperId))
        return peer.copy(presentation = incoming.metadata(), avatar = image)
    }

    suspend fun saveImage(uri: Uri) {
        // Sample before decode; never retain a URI grant or transmit source metadata.
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0 || options.outHeight <= 0) return
        var sample = 1
        while (maxOf(options.outWidth, options.outHeight) / sample > 768) sample *= 2
        options.inJustDecodeBounds = false
        options.inSampleSize = sample
        val original = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return
        val scale = minOf(1f, 384f / maxOf(original.width, original.height))
        val scaled = Bitmap.createScaledBitmap(original, maxOf(1, (original.width * scale).toInt()),
            maxOf(1, (original.height * scale).toInt()), true)
        val bytes = ByteArrayOutputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 75, it); it.toByteArray() }
        if (scaled !== original) scaled.recycle()
        original.recycle()
        if (bytes.size <= 65536) File(context.filesDir, "device-preview.jpg").writeBytes(bytes)
        publishAll()
    }

    fun useWallpaper() { File(context.filesDir, "device-preview.jpg").delete() }

}
