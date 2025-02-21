package com.enaboapps.switchify.utils

import android.content.Context
import java.lang.ref.WeakReference

object Resources {
    /**
     * Weak reference to the application context.
     */
    private var contextRef: WeakReference<Context>? = null

    /**
     * Initializes the Resources class with the application context.
     *
     * @param context The application context.
     */
    fun init(context: Context) {
        contextRef = WeakReference(context)
    }

    /**
     * Gets the string resource with the specified resource ID.
     *
     * @param resourceId The resource ID.
     * @return The string resource with the specified resource ID.
     */
    fun getString(resourceId: Int): String {
        return contextRef?.get()?.getString(resourceId) ?: ""
    }

    /**
     * Gets the string resource with the specified resource ID and arguments.
     *
     * @param resourceId The resource ID.
     * @param args The arguments.
     * @return The string resource with the specified resource ID and arguments.
     */
    fun getString(resourceId: Int, vararg args: Any): String {
        return contextRef?.get()?.getString(resourceId, *args) ?: ""
    }
}