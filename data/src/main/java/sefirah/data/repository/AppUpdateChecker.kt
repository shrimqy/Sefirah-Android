package sefirah.data.repository

import android.content.Context
import android.content.pm.PackageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateChecker @Inject constructor(
    private val context: Context,
    private val releaseRepository: ReleaseRepository,
) {
    suspend fun checkForUpdate() : ReleaseRepository.Result {
        val packageInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val version = packageInfo.versionName

        return withContext(Dispatchers.IO) {
            releaseRepository.getRelease(version!!)
        }
    }
}