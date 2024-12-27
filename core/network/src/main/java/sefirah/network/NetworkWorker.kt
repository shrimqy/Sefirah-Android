package sefirah.network

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class NetworkWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {
    override fun doWork(): Result {
        TODO("Not yet implemented")
    }
}