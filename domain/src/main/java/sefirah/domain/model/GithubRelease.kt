package sefirah.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubReleaseResponse(
    @SerialName("tag_name") val tagName: String,
    @SerialName("body") val body: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("assets") val assets: List<GitHubAsset>
)

@Serializable
data class GitHubAsset(
    @SerialName("browser_download_url") val browserDownloadUrl: String
)