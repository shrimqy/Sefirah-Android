package sefirah.domain.model

import android.os.Build

/**
 * Contains information about the latest release.
 */
data class Release(
    val version: String,
    val info: String,
    val releaseLink: String,
    private val assets: List<String>,
) {

    /**
     * Get download link of latest release from the assets.
     * @return download link of latest release.
     */
    fun getDownloadLink(): String {
        return assets.find { it.contains("sefirah$-") } ?: assets[0]
    }

    /**
     * Assets class containing download url.
     */
    data class Assets(val downloadLink: String)
}
