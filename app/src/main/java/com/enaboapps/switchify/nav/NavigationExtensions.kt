package com.enaboapps.switchify.nav

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

/**
 * Navigates only while the current destination is resumed, so a bounced or
 * repeated switch press cannot push the same destination twice.
 */
fun NavController.navigateIfResumed(
    route: String,
    builder: NavOptionsBuilder.() -> Unit = {}
) {
    val state = currentBackStackEntry?.lifecycle?.currentState ?: Lifecycle.State.RESUMED
    if (state.isAtLeast(Lifecycle.State.RESUMED)) {
        navigate(route, builder)
    }
}
