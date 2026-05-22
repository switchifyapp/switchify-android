package com.enaboapps.switchify.service.llm

import android.graphics.Bitmap

/**
 * Hands the Reply Drafter screenshot from the accessibility service to
 * ReplyDrafterActivity. The bitmap is far larger than the Intent transaction
 * limit, so it is held here and consumed once by the activity.
 */
object ReplyDrafterScreenshotHolder {
    private var bitmap: Bitmap? = null

    fun set(bitmap: Bitmap) {
        this.bitmap = bitmap
    }

    fun consume(): Bitmap? {
        val held = bitmap
        bitmap = null
        return held
    }
}
