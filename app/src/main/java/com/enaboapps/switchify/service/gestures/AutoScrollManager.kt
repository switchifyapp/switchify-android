package com.enaboapps.switchify.service.gestures

import android.content.Context
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.gestures.data.GestureData
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
     * Checks if auto-scrolling is enabled.
     * @return True if auto-scrolling is enabled, false otherwise.
     */
    private fun isAutoScrollEnabled(): Boolean {
        return preferenceManager.getBooleanValue(PreferenceManager.Keys.PREFERENCE_KEY_AUTO_SCROLL)
    }

    /**
     * Gets the delay for auto-scrolling.
     * @return The delay for auto-scrolling.
     */
    private fun getAutoScrollDelay(): Long {
        return preferenceManager.getLongValue(PreferenceManager.Keys.PREFERENCE_KEY_AUTO_SCROLL_DELAY)
    }

    /**
     * Starts auto-scrolling repeatedly.
     * @param gestureData The gesture data to use for auto-scrolling.
     * @return True if auto-scrolling was started, false otherwise.
     */
    fun startAutoScroll(gestureData: GestureData): Boolean {
        if (!isAutoScrollEnabled() || isAutoScrolling || !gestureData.isScroll() || scrollJob != null) return false
        ServiceMessageHUD.instance.showMessage(
            R.string.hud_auto_scroll_started,
            ServiceMessageHUD.MessageType.DISAPPEARING
        )
        isAutoScrolling = true
        scrollJob = scope.launch {
            while (isAutoScrolling) {
                gestureData.performAutoScroll(GestureManager.getInstance())
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
        if (!isAutoScrollEnabled()) return false

        if (scrollJob != null && isAutoScrolling) {
            scrollJob?.cancel()
            scrollJob = null
            isAutoScrolling = false
            ServiceMessageHUD.instance.showMessage(
                R.string.hud_auto_scroll_stopped,
                ServiceMessageHUD.MessageType.DISAPPEARING
            )
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
}