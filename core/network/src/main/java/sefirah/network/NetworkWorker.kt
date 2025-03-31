package sefirah.network

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import sefirah.network.NetworkService.Companion.REMOTE_INFO
import sefirah.database.AppRepository
import sefirah.database.model.toDomain

@HiltWorker
class NetworkWorker @AssistedInject constructor(
    private val appRepository: AppRepository,
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        val deviceId = inputData.getString(REMOTE_INFO)
        val remoteDevice = deviceId?.let { appRepository.getLastConnectedDevice() }
        
        if (remoteDevice != null) {
            val intent = Intent(applicationContext, NetworkService::class.java).apply {
                action = NetworkService.Companion.Actions.START.name
                putExtra(REMOTE_INFO, remoteDevice.toDomain())
            }
            Log.d("worker", "Worker started Service")
            applicationContext.startForegroundService(intent)
        }
        return Result.success()
    }
}