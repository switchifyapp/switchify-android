package com.enaboapps.switchify.service.gestures

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.data.store.GesturePatternStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.lang.ref.WeakReference

/**
 * Object responsible for recording new gesture patterns.
 *
 * This singleton maintains a list of recorded gestures and provides methods to start/stop recording
 * and save the recorded pattern to the GesturePatternStore.
 */
object GesturePatternRecorder {
    private val tag = "GesturePatternRecorder"
    private var contextRef: WeakReference<Context>? = null
    private var gesturePatternStoreRef: WeakReference<GesturePatternStore>? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private var isRecording = false
    private val recordedGestures = mutableListOf<GestureData>()

    /**
     * Initializes the recorder with the application context.
     * Must be called before using any other methods.
     *
     * @param appContext The application context
     */
    fun initialize(appContext: Context) {
        contextRef = WeakReference(appContext.applicationContext)
        gesturePatternStoreRef = WeakReference(GesturePatternStore(appContext))
        Log.i(tag, "GesturePatternRecorder initialized")
    }

    /**
     * Gets the current context, or null if not initialized.
     */
    private fun getContext(): Context? {
        return contextRef?.get()
    }

    /**
     * Gets the gesture pattern store, or null if not initialized.
     */
    private fun getGesturePatternStore(): GesturePatternStore? {
        return gesturePatternStoreRef?.get()
    }

    /**
     * Starts recording a new gesture pattern.
     * Clears any previously recorded gestures.
     */
    fun startRecording() {
        if (getContext() == null) {
            Log.e(tag, "GesturePatternRecorder not initialized")
            return
        }

        if (isRecording) {
            Log.w(tag, "Recording already in progress")
            return
        }

        recordedGestures.clear()
        isRecording = true
        Log.i(tag, "Started recording new gesture pattern")
    }

    /**
     * Stops recording the current gesture pattern.
     *
     * @param name The name to give to the recorded pattern
     * @return The ID of the saved pattern, or empty string if no gestures were recorded
     */
    fun stopRecording(name: String): String {
        if (getContext() == null) {
            Log.e(tag, "GesturePatternRecorder not initialized")
            return ""
        }

        if (!isRecording) {
            Log.w(tag, "No recording in progress")
            return ""
        }

        isRecording = false

        if (recordedGestures.isEmpty()) {
            Log.w(tag, "No gestures recorded")
            return ""
        }

        val store = getGesturePatternStore() ?: run {
            Log.e(tag, "GesturePatternStore not available")
            return ""
        }

        val patternId = store.addPattern(name, recordedGestures.toList())
        Log.i(tag, "Saved gesture pattern with ID: $patternId")
        return patternId
    }

    /**
     * Adds a gesture to the current recording.
     *
     * @param gesture The gesture data to add
     * @return true if the gesture was added, false if not recording
     */
    fun addGesture(gesture: GestureData): Boolean {
        if (getContext() == null) {
            Log.e(tag, "GesturePatternRecorder not initialized")
            return false
        }

        if (!isRecording) {
            Log.w(tag, "Cannot add gesture: not recording")
            return false
        }

        recordedGestures.add(gesture)
        Log.d(tag, "Added gesture to recording, total gestures: ${recordedGestures.size}")
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
        Log.i(tag, "Cleared recorded gestures")
    }
}