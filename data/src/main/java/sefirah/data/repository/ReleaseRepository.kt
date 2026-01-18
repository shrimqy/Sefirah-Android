package sefirah.data.repository

import android.util.Log
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.first
import sefirah.domain.interfaces.PreferencesRepository
import sefirah.domain.model.GitHubReleaseResponse
import sefirah.domain.model.Release
import sefirah.network.NetworkHelper
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReleaseRepository @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val networkHelper: NetworkHelper
) {
    suspend fun getRelease(currentVersion: String): Result {
        try {
            val lastChecked = preferencesRepository.readLastCheckedForUpdate().first()
            val now = Instant.now()

            val nextCheckTime = Instant.ofEpochMilli(lastChecked).plus(3, ChronoUnit.DAYS)

            if (now.isBefore(nextCheckTime)) {
                return Result.NoNewUpdate
            }

            val response = networkHelper.client.get(
                "https://api.github.com/repos/shrimqy/Sefirah-Android/releases/latest"
            ) {
                contentType(ContentType.Application.Json)
            }

            preferencesRepository.saveLastCheckedForUpdate(now.toEpochMilli())

            val githubRelease: GitHubReleaseResponse = response.body()

            val latestRelease = Release(
                version = githubRelease.tagName,
                info = githubRelease.body,
                releaseLink = githubRelease.htmlUrl,
                assets = githubRelease.assets.map { it.browserDownloadUrl }
            )
            
            return if (isNewVersion(currentVersion, latestRelease.version)) {
                Log.d("ReleaseRepository", "New update available: ${latestRelease.version}")
                Result.NewUpdate(latestRelease)
            } else {
                Result.NoNewUpdate
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.NoNewUpdate
        }
    }

    private fun isNewVersion(
        versionName: String,
        versionTag: String,
    ): Boolean {
        // Removes prefixes like "v"
        val newVersion = versionTag.replace("[^\\d.]".toRegex(), "")
        val oldVersion = versionName.replace("[^\\d.]".toRegex(), "")

        val newSemVer = newVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val oldSemVer = oldVersion.split(".").map { it.toIntOrNull() ?: 0 }

        // Fix the comparison logic
        for (i in 0 until minOf(newSemVer.size, oldSemVer.size)) {
            if (newSemVer[i] > oldSemVer[i]) {
                return true
            } else if (newSemVer[i] < oldSemVer[i]) {
                return false
            }
        }
        
        // If all common segments are equal, the longer one is newer
        return newSemVer.size > oldSemVer.size
    }

    sealed interface Result {
        data class NewUpdate(val release: Release) : Result
        data object NoNewUpdate : Result
    }
}