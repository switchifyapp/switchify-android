package com.enaboapps.switchify.service.gestures

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.patterns.store.GesturePatternStore
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Object responsible for recording new gesture patterns.
 *
 * This singleton maintains a list of recorded gestures and provides methods to start/stop recording
 * and save the recorded pattern to the GesturePatternStore.
 */
object GesturePatternRecorder {
    private const val TAG = "GesturePatternRecorder"

    private var isRecording = false
    private val recordedGestures = mutableListOf<GestureData>()

    /**
     * Creates a new gesture pattern store.
     * @param context The application context.
     * @return The new gesture pattern store.
     */
    private fun createGesturePatternStore(context: Context): GesturePatternStore {
        return GesturePatternStore(context)
    }

    /**
     * Starts recording a new gesture pattern.
     * Clears any previously recorded gestures.
     */
    fun startRecording() {
        if (isRecording) {
            Log.w(TAG, "Recording already in progress")
            return
        }

        recordedGestures.clear()
        isRecording = true

        ServiceMessageHUD.instance.showMessage(
            R.string.started_recording,
            ServiceMessageHUD.MessageType.DISAPPEARING
        )

        Log.i(TAG, "Started recording new gesture pattern")
    }

    /**
     * Saves the current recording.
     */
    fun saveRecording(context: Context) {
        if (!isRecording) {
            Log.w(TAG, "No recording in progress")
            return
        }

        isRecording = false

        if (recordedGestures.isEmpty()) {
            Log.w(TAG, "No gestures recorded")
            return
        }

        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val store = createGesturePatternStore(context)

            val name = recordedGestures.joinToString(separator = ", ") { it.gestureType.name }
            val patternId = store.addPattern(name, recordedGestures)
            Log.i(TAG, "Saved gesture pattern with ID: $patternId")
            recordedGestures.clear()

            ServiceMessageHUD.instance.showMessage(
                R.string.saved_gesture_pattern,
                ServiceMessageHUD.MessageType.DISAPPEARING
            )
        }
    }

    /**
     * Cancels the current recording.
     */
    fun cancelRecording() {
        if (isRecording) {
            recordedGestures.clear()
            isRecording = false
            ServiceMessageHUD.instance.showMessage(
                R.string.recording_canceled,
                ServiceMessageHUD.MessageType.DISAPPEARING
            )
        }
    }

    /**
     * Adds a gesture to the current recording.
     *
     * @param gesture The gesture data to add
     * @return true if the gesture was added, false if not recording
     */
    fun addGesture(gesture: GestureData): Boolean {
        if (!isRecording) {
            Log.w(TAG, "Cannot add gesture: not recording")
            return false
        }

        recordedGestures.add(gesture)
        Log.d(TAG, "Added gesture to recording, total gestures: ${recordedGestures.size}")
        return true
    }

    /**
     * Checks if recording is currently in progress.
     *
     * @return true if recording, false otherwise
     */
    fun isRecording(): Boolean = isRecording

    /**
     * Gets the number of gestures recorded so far.
     *
     * @return The count of recorded gestures
     */
    fun getRecordedGestureCount(): Int = recordedGestures.size

    /**
     * Clears the current recording without saving.
     */
    fun clearRecording() {
        recordedGestures.clear()
        Log.i(TAG, "Cleared recorded gestures")
    }
}