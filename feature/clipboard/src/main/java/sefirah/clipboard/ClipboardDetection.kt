/*
 * Acknowledgment:
 * Portions of this code are adapted from XClipper by Kaustubh Patange.
 * Licensed under the Apache License 2.0.
 */

package sefirah.clipboard

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import sefirah.clipboard.ClipboardDetection.AEvent.Companion.copyKeyWords
import sefirah.clipboard.extensions.StripArrayList

typealias Predicate = (ClipboardDetection.AEvent) -> Boolean

class ClipboardDetection(
    private val copyWord: String = "Copy"
) {
    private val typeViewSelectionChangeEvent: StripArrayList<AEvent> = StripArrayList(2)
    private val eventList: StripArrayList<Int> = StripArrayList(4)
    private var lastEvent: AEvent? = null

    /**
     * Add an [AccessibilityEvent] to the striping array list.
     */
    fun addEvent(c: Int) {
        eventList.add(c)
    }

    /** Some hacks I figured out which would trigger copy/cut for Android 10 */
    fun getSupportedEventTypes(event: AccessibilityEvent?, predicate: Predicate? = null): Boolean {
        if (event == null) return false

        val clipEvent = AEvent.from(event)
        if (predicate?.invoke(clipEvent) == true) return false
        return detectAppropriateEvents(event = clipEvent)
    }


    private fun detectAppropriateEvents(event: AEvent): Boolean {
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            typeViewSelectionChangeEvent.add(event)
        }

        /**
         * This second condition is a hack whenever someone clicks copy or cut context button,
         * it detects this behaviour as copy.
         *
         * Disadvantages: Event TYPE_VIEW_CLICKED is fired whenever you touch on the screen,
         * this means if there is a text which contains "copy" it's gonna consider that as a
         * copy behaviour.
         */
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED && event.text != null
            && ((event.contentDescription?.length ?: 0) < MAX_COPY_WORD_DETECTION_LENGTH
                    && event.contentDescription?.contains(copyWord, true) == true
                    || ((event.text?.toString()?.length ?: 0) < MAX_COPY_WORD_DETECTION_LENGTH
                    && event.text?.toString()?.contains(copyWord, true) == true)
                    || event.contentDescription == "Cut" || event.contentDescription == copyWord)
        ) {
            Log.d(TAG,"Copy captured - 2")
            return true
        }

        /**
         * We captured the last two [AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED] in list &
         * will try to determine if they are valid for copy action!
         */
        if (typeViewSelectionChangeEvent.size == 2) {
            val firstEvent = typeViewSelectionChangeEvent[0]
            val secondEvent = typeViewSelectionChangeEvent[1]
            if (secondEvent.fromIndex == secondEvent.toIndex) {
                val success =
                    (firstEvent.packageName == secondEvent.packageName && firstEvent.fromIndex != firstEvent.toIndex
                            && secondEvent.className == firstEvent.className) && secondEvent.text.toString() == firstEvent.text.toString()
                typeViewSelectionChangeEvent.clear()
                if (success) {
                    Log.d(TAG,"Copy captured - 3")
                    return true
                }
            }
        }

        if ((event.contentChangeTypes ?: (0 and AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE)) == 1
            && event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && lastEvent != null
        ) {
            val previousEvent = lastEvent!!

            if (previousEvent.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                /* && previousEvent.ScrollX == -1 && previousEvent.ScrollY == -1*/ // TODO: See if you need any additional checks, uncomment it then
                && previousEvent.text?.size == 1
                && (previousEvent.text?.toString()?.contains(copyWord, true) == true
                        || previousEvent.contentDescription?.contains(copyWord, true) == true)) {
                Log.d(TAG,"Copy captured - 1.1")
                return true
            }
        }

        /*if (event.sourceActions.containsAll(copyActions) && event.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
            Log.d(TAG,"Copy captured - 1.2")
            return true
        }*/
        
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED && event.className == "${Toast::class.qualifiedName}\$TN"
            && event.text != null && event.text?.toString()?.contains(copyKeyWords) == true) {
            Log.d(TAG,"Copy captured - 1.2")
            return true
        }

        lastEvent = event.clone()
        return false
    }


    data class AEvent(
        var eventType: Int? = null,
        var eventTime: Long? = null,
        var packageName: CharSequence? = null,
        var movementGranularity: Int? = null,
        var action: Int? = null,
        var className: CharSequence? = null,
        var text: List<CharSequence?>? = null,
        var contentDescription: CharSequence? = null,
        var contentChangeTypes: Int? = null,
        var currentItemIndex: Int? = null,
        var fromIndex: Int? = null,
        var toIndex: Int? = null,
        var scrollX: Int? = null,
        var scrollY: Int? = null,
        var sourceActions: List<AccessibilityNodeInfo.AccessibilityAction> = emptyList(),
    ) {
        companion object {
            internal val copyActions = listOf<AccessibilityNodeInfo.AccessibilityAction>(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_LONG_CLICK,
            )
            internal val copyKeyWords = "(copied)|(Copied)|(clipboard)".toRegex()

            fun from(event: AccessibilityEvent): AEvent {
                return AEvent(
                    eventType = event.eventType,
                    eventTime = event.eventTime,
                    packageName = event.packageName,
                    movementGranularity = event.movementGranularity,
                    action = event.action,
                    className = event.className,
                    text = event.text,
                    contentChangeTypes = event.contentChangeTypes,
                    contentDescription = event.contentDescription,
                    currentItemIndex = event.currentItemIndex,
                    fromIndex = event.fromIndex,
                    toIndex = event.toIndex,
                    scrollX = event.scrollX,
                    scrollY = event.scrollY,
                    sourceActions = event.source?.actionList ?: emptyList()
                )
            }
        }
    }

    private fun AEvent.clone(): AEvent = this.copy(text = ArrayList(this.text ?: listOf()))

    private companion object {
        private const val TAG = "Clipboard Detector"
        private const val MAX_COPY_WORD_DETECTION_LENGTH = 30
    }
}