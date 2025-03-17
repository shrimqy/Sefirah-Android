package sefirah.common.util

object MimeType {
    const val TYPE_TEXT: String = "text/plain"
    const val TYPE_IMAGE: String = "image"
    const val TYPE_VIDEO: String = "video"
    const val TYPE_AUDIO: String = "audio"

    fun isTypeText(mimeType: String): Boolean {
        return mimeType.startsWith(TYPE_TEXT)
    }

    fun isTypeImage(mimeType: String): Boolean {
        return mimeType.startsWith(TYPE_IMAGE)
    }

    fun isTypeVideo(mimeType: String): Boolean {
        return mimeType.startsWith(TYPE_VIDEO)
    }

    fun isTypeAudio(mimeType: String): Boolean {
        return mimeType.startsWith(TYPE_AUDIO)
    }

    fun postfixOf(mimeType: String): String {
        return mimeType.substring(mimeType.lastIndexOf('/') + 1)
    }
}