package com.enaboapps.switchify.service.actions

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.window.MessageSeverity
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Centralized manager for performing audio-related actions.
 * This object provides a single point of control for all audio system actions.
 */
object AudioActionManager {
    private const val TAG = "AudioActionManager"
    private var accessibilityService: SwitchifyAccessibilityService? = null
    private var audioManager: AudioManager? = null
    private val playbackPolicy = MediaPlaybackPolicy()
    private var playbackToggleJob: Job? = null

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

    fun isMusicActive(): Boolean = audioManager?.isMusicActive == true

    /** Whether media controls belong in the main menu right now. */
    fun playbackState(): MediaPlaybackState {
        if (audioManager == null) return MediaPlaybackState.NONE
        return playbackPolicy.observe(
            isMusicActive = isMusicActive(),
            supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            now = SystemClock.uptimeMillis()
        )
    }

    fun togglePlayback() {
        val service = accessibilityService ?: return
        playbackToggleJob?.cancel()
        playbackToggleJob = service.getServiceScope().launch(Dispatchers.Main.immediate) {
            val active = MediaPlaybackToggle(
                isMusicActive = ::isMusicActive,
                dispatchToggle = GlobalActionManager::toggleMediaPlayback
            ).toggle() ?: return@launch
            playbackPolicy.noteToggled(SystemClock.uptimeMillis())
            ServiceMessageHUD.instance.showMessage(
                if (active) R.string.hud_media_playing else R.string.hud_media_paused,
                ServiceMessageHUD.MessageType.DISAPPEARING,
                ServiceMessageHUD.Time.SHORT,
                severity = MessageSeverity.Info
            )
        }
    }

    /**
     * Increase the volume of the accessibility stream.
     *
     * @return true if successful, false otherwise
     */
    fun volumeUp(): Boolean {
        return audioManager?.let {
            it.adjustStreamVolume(
                AudioManager.STREAM_ACCESSIBILITY,
                AudioManager.ADJUST_RAISE,
                AudioManager.FLAG_SHOW_UI
            )
            true
        } ?: false
    }

    /**
     * Decrease the volume of the accessibility stream.
     *
     * @return true if successful, false otherwise
     */
    fun volumeDown(): Boolean {
        return audioManager?.let {
            it.adjustStreamVolume(
                AudioManager.STREAM_ACCESSIBILITY,
                AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI
            )
            true
        } ?: false
    }

    /**
     * Set the volume to maximum.
     *
     * @return true if successful, false otherwise
     */
    fun setFullVolume(): Boolean {
        return audioManager?.let {
            it.setStreamVolume(
                AudioManager.STREAM_ACCESSIBILITY,
                it.getStreamMaxVolume(AudioManager.STREAM_ACCESSIBILITY),
                AudioManager.FLAG_SHOW_UI
            )
            true
        } ?: false
    }

    /**
     * Mute the accessibility stream.
     *
     * @return true if successful, false otherwise
     */
    fun mute(): Boolean {
        return audioManager?.let {
            it.setStreamVolume(
                AudioManager.STREAM_ACCESSIBILITY,
                0,
                AudioManager.FLAG_SHOW_UI
            )
            true
        } ?: false
    }

    /**
     * Set the volume to 50% of maximum.
     *
     * @return true if successful, false otherwise
     */
    fun setHalfVolume(): Boolean {
        return audioManager?.let {
            it.setStreamVolume(
                AudioManager.STREAM_ACCESSIBILITY,
                it.getStreamMaxVolume(AudioManager.STREAM_ACCESSIBILITY) / 2,
                AudioManager.FLAG_SHOW_UI
            )
            true
        } ?: false
    }

    /**
     * Skip to the next media track. Dispatches both KEY_DOWN and KEY_UP
     * because some apps respond only to one or the other.
     *
     * @return true if successful, false otherwise
     */
    fun nextTrack(): Boolean {
        return audioManager?.let {
            it.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT))
            it.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT))
            true
        } ?: run {
            Log.e(TAG, "Failed to skip to next track: AudioManager is null")
            false
        }
    }

    /**
     * Skip to the previous media track. Dispatches both KEY_DOWN and KEY_UP
     * because some apps respond only to one or the other.
     *
     * @return true if successful, false otherwise
     */
    fun previousTrack(): Boolean {
        return audioManager?.let {
            it.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
            it.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
            true
        } ?: run {
            Log.e(TAG, "Failed to skip to previous track: AudioManager is null")
            false
        }
    }

    /**
     * Clear the references when the service is destroyed.
     */
    fun cleanup() {
        playbackToggleJob?.cancel()
        playbackToggleJob = null
        accessibilityService = null
        audioManager = null
        Log.d(TAG, "AudioActionManager cleaned up")
    }
}
