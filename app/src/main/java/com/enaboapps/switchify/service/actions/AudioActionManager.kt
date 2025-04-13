package com.enaboapps.switchify.service.actions

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService

/**
 * Centralized manager for performing audio-related actions.
 * This object provides a single point of control for all audio system actions.
 */
object AudioActionManager {
    private const val TAG = "AudioActionManager"
    private var accessibilityService: SwitchifyAccessibilityService? = null
    private var audioManager: AudioManager? = null

    /**
     * Initialize the AudioActionManager with the accessibility service.
     *
     * @param service The SwitchifyAccessibilityService instance
     */
    fun init(service: SwitchifyAccessibilityService) {
        accessibilityService = service
        audioManager = service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        Log.d(TAG, "AudioActionManager initialized")
    }

    /**
     * Increase the volume of the accessibility stream.
     *
     * @return true if successful, false otherwise
     */
    fun volumeUp(): Boolean {
        return IAPHandler.runIfProPurchased(accessibilityService!!.applicationContext) {
            audioManager?.let {
                it.adjustStreamVolume(
                    AudioManager.STREAM_ACCESSIBILITY,
                    AudioManager.ADJUST_RAISE,
                    AudioManager.FLAG_SHOW_UI
                )
                true
            } == true
        }
    }

    /**
     * Decrease the volume of the accessibility stream.
     *
     * @return true if successful, false otherwise
     */
    fun volumeDown(): Boolean {
        return IAPHandler.runIfProPurchased(accessibilityService!!.applicationContext) {
            audioManager?.let {
                it.adjustStreamVolume(
                    AudioManager.STREAM_ACCESSIBILITY,
                    AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI
                )
                true
            } == true
        }
    }

    /**
     * Set the volume to maximum.
     *
     * @return true if successful, false otherwise
     */
    fun setFullVolume(): Boolean {
        return IAPHandler.runIfProPurchased(accessibilityService!!.applicationContext) {
            audioManager?.let {
                it.setStreamVolume(
                    AudioManager.STREAM_ACCESSIBILITY,
                    it.getStreamMaxVolume(AudioManager.STREAM_ACCESSIBILITY),
                    AudioManager.FLAG_SHOW_UI
                )
                true
            } == true
        }
    }

    /**
     * Mute the accessibility stream.
     *
     * @return true if successful, false otherwise
     */
    fun mute(): Boolean {
        return IAPHandler.runIfProPurchased(accessibilityService!!.applicationContext) {
            audioManager?.let {
                it.setStreamVolume(
                    AudioManager.STREAM_ACCESSIBILITY,
                    0,
                    AudioManager.FLAG_SHOW_UI
                )
                true
            } == true
        }
    }

    /**
     * Set the volume to 50% of maximum.
     *
     * @return true if successful, false otherwise
     */
    fun setHalfVolume(): Boolean {
        return IAPHandler.runIfProPurchased(accessibilityService!!.applicationContext) {
            audioManager?.let {
                it.setStreamVolume(
                    AudioManager.STREAM_ACCESSIBILITY,
                    it.getStreamMaxVolume(AudioManager.STREAM_ACCESSIBILITY) / 2,
                    AudioManager.FLAG_SHOW_UI
                )
                true
            } == true
        }
    }

    /**
     * Clear the references when the service is destroyed.
     */
    fun cleanup() {
        accessibilityService = null
        audioManager = null
        Log.d(TAG, "AudioActionManager cleaned up")
    }
}