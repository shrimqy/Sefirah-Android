package sefirah.clipboard

import sefirah.domain.model.ClipboardMessage

interface ClipboardHandler {
    fun start()
    fun stop()
    fun setClipboard(clipboard: ClipboardMessage)
}
