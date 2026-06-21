package com.enaboapps.switchify.service.gestures

import android.content.Context
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.core.Tasks
import com.enaboapps.switchify.service.window.MessageSeverity
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages auto-scrolling functionality.
 */
class AutoScrollManager private constructor() {
    private var isAutoScrolling = false
    private lateinit var preferenceManager: PreferenceManager
    private val scope = CoroutineScope(Dispatchers.IO)
    private var scrollJob: Job? = null
    private var autoScrollEnabledProviderForTesting: (() -> Boolean)? = null
    private var autoScrollDelayProviderForTesting: (() -> Long)? = null
    private var autoScrollPerformerForTesting: ((GestureData) -> Boolean)? = null
    private var suppressHudForTesting = false
    private var messageRecorderForTesting: ((Int) -> Unit)? = null

    companion object {
        private var instance: AutoScrollManager? = null

        /**
         * Gets the singleton instance of the AutoScrollManager.
         * @return The singleton instance of the AutoScrollManager.
         */
        fun getInstance(): AutoScrollManager {
            return instance ?: synchronized(this) {
                instance ?: AutoScrollManager().also { instance = it }
            }
        }
    }

    /**
     * Initializes the AutoScrollManager with the given context.
     * @param context The context to use for initializing the AutoScrollManager.
     */
    fun init(context: Context) {
        preferenceManager = PreferenceManager(context)
    }

    /**
     * Checks if auto-scrolling is enabled in the preferences.
     * @return True if auto-scrolling is enabled, false otherwise.
     */
    private fun isAutoScrollEnabledInPreferences(): Boolean {
        autoScrollEnabledProviderForTesting?.let { return it() }
        return preferenceManager.getBooleanValue(PreferenceManager.Keys.PREFERENCE_KEY_AUTO_SCROLL)
    }

    /**
     * Gets the delay for auto-scrolling.
     * @return The delay for auto-scrolling.
     */
    private fun getAutoScrollDelay(): Long {
        autoScrollDelayProviderForTesting?.let { return it() }
        return preferenceManager.getLongValue(PreferenceManager.Keys.PREFERENCE_KEY_AUTO_SCROLL_DELAY)
    }

    /**
     * Starts auto-scrolling repeatedly.
     * @param gestureData The gesture data to use for auto-scrolling.
     * @return True if auto-scrolling was started, false otherwise.
     */
    fun startAutoScroll(gestureData: GestureData): Boolean {
        if (!isAutoScrollEnabledInPreferences() || isAutoScrolling || !gestureData.isScroll() || scrollJob != null) return false
        val result = GestureModePolicy.canStartAutoScroll(
            currentRepeatEnabled = GestureRepeatManager.instance.isAutoRepeatEnabled(),
            currentRearmEnabled = GestureLockManager.instance.isAutoReenableEnabled(),
            isGestureLockEnabled = GestureLockManager.instance.isLocked()
        )
        result.blockedReasonResId?.let {
            showMessage(it, MessageSeverity.Warning)
            return false
        }
        showMessage(R.string.hud_auto_scroll_started, MessageSeverity.Success)
        isAutoScrolling = true
        Tasks.getInstance().onOngoingTaskStarted()
        scrollJob = scope.launch {
            while (isAutoScrolling) {
                performAutoScroll(gestureData)
                if (isAutoScrolling) delay(getAutoScrollDelay())
            }
        }
        return true // Return true if auto-scrolling was started
    }

    /**
     * Stops auto-scrolling.
     * @return True if auto-scrolling was stopped, false otherwise.
     */
    fun stopAutoScroll(): Boolean {
        if (!isAutoScrollEnabledInPreferences()) return false

        if (scrollJob != null && isAutoScrolling) {
            scrollJob?.cancel()
            scrollJob = null
            isAutoScrolling = false
            showMessage(R.string.hud_auto_scroll_stopped, MessageSeverity.Info)
            return true
        }

        return false
    }

    /**
     * Checks if auto-scrolling is currently enabled.
     * @return True if auto-scrolling is currently enabled, false otherwise.
     */
    fun isAutoScrolling(): Boolean {
        return isAutoScrolling
    }

    private fun showMessage(messageResId: Int, severity: MessageSeverity) {
        messageRecorderForTesting?.invoke(messageResId)
        if (suppressHudForTesting) return
        ServiceMessageHUD.instance.showMessage(
            messageResId,
            ServiceMessageHUD.MessageType.DISAPPEARING,
            severity = severity
        )
    }

    private fun performAutoScroll(gestureData: GestureData): Boolean {
        return autoScrollPerformerForTesting?.invoke(gestureData)
            ?: gestureData.performAutoScroll()
    }

    internal fun setAutoScrollEnabledProviderForTesting(provider: (() -> Boolean)?) {
        autoScrollEnabledProviderForTesting = provider
    }

    internal fun setAutoScrollDelayProviderForTesting(provider: (() -> Long)?) {
        autoScrollDelayProviderForTesting = provider
    }

    internal fun setAutoScrollPerformerForTesting(performer: ((GestureData) -> Boolean)?) {
        autoScrollPerformerForTesting = performer
    }

    internal fun setSuppressHudForTesting(suppress: Boolean) {
        suppressHudForTesting = suppress
    }

    internal fun setMessageRecorderForTesting(recorder: ((Int) -> Unit)?) {
        messageRecorderForTesting = recorder
    }

    internal fun resetForTesting() {
        scrollJob?.cancel()
        scrollJob = null
        isAutoScrolling = false
        autoScrollEnabledProviderForTesting = null
        autoScrollDelayProviderForTesting = null
        autoScrollPerformerForTesting = null
        suppressHudForTesting = false
        messageRecorderForTesting = null
    }
}
