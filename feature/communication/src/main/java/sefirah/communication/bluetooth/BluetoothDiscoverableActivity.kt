package sefirah.communication.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import sefirah.common.notifications.AppNotifications
import sefirah.domain.interfaces.NetworkManager
import sefirah.domain.model.BluetoothPairingResult
import javax.inject.Inject

@AndroidEntryPoint
class BluetoothDiscoverableActivity : FragmentActivity() {

    @Inject lateinit var networkManager: NetworkManager

    private val discoverableLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            sendResult(result.resultCode > 0)
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return

        val sourceDeviceId = intent.getStringExtra(EXTRA_SOURCE_DEVICE_ID)
        if (sourceDeviceId.isNullOrBlank()) {
            finish()
            return
        }

        NotificationManagerCompat.from(this).cancel(AppNotifications.BLUETOOTH_DISCOVERABLE_REQUEST_ID + sourceDeviceId.hashCode())

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            sendResult(false)
            finish()
            return
        }

        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_DURATION_SECONDS)
        }

        runCatching { discoverableLauncher.launch(discoverableIntent) }
            .onFailure {
                sendResult(false)
                finish()
            }
    }

    private fun sendResult(granted: Boolean) {
        val sourceDeviceId = intent.getStringExtra(EXTRA_SOURCE_DEVICE_ID) ?: return
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
        val deviceName = bluetoothManager?.adapter?.name
        lifecycleScope.launch {
            networkManager.sendMessage(sourceDeviceId, BluetoothPairingResult(granted, deviceName))
        }
    }

    companion object {
        private const val EXTRA_SOURCE_DEVICE_ID = "extra_source_device_id"
        private const val DISCOVERABLE_DURATION_SECONDS = 120

        fun createIntent(context: Context, sourceDeviceId: String): Intent =
            Intent(context, BluetoothDiscoverableActivity::class.java).apply {
                putExtra(EXTRA_SOURCE_DEVICE_ID, sourceDeviceId)
            }
    }
}
