package com.enaboapps.switchify.screens.settings.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import com.enaboapps.switchify.nav.NavigationRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter

object SettingsSearchHighlight {
    data class Target(val route: String, val titleResId: Int)

    var pending by mutableStateOf<Target?>(null)
        private set

    fun request(entry: SettingsSearchEntry) {
        pending = Target(entry.route, entry.titleResId)
    }

    fun clear() {
        pending = null
    }

    fun clearUnless(route: String?) {
        val target = pending ?: return
        if (route != target.route && route != NavigationRoute.SettingsSearch.name) pending = null
    }

    fun initialTab(route: String, default: Int): Int {
        val target = pending?.takeIf { it.route == route } ?: return default
        return GeneratedSettingsSearchIndex.entries
            .firstOrNull { it.route == route && it.titleResId == target.titleResId }
            ?.tab ?: default
    }
}

private const val HIGHLIGHT_DURATION_MS = 2500L

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.settingsSearchHighlight(titleResId: Int?): Modifier {
    if (titleResId == null) return this
    val requester = remember { BringIntoViewRequester() }
    var active by remember { mutableStateOf(false) }
    LaunchedEffect(titleResId) {
        snapshotFlow { SettingsSearchHighlight.pending?.titleResId == titleResId }
            .filter { it }
            .collect {
                SettingsSearchHighlight.clear()
                active = true
                requester.bringIntoView()
                delay(HIGHLIGHT_DURATION_MS)
                active = false
            }
    }
    val alpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "settingsSearchHighlight"
    )
    val color = MaterialTheme.colorScheme.primary
    return this
        .bringIntoViewRequester(requester)
        .drawWithContent {
            drawContent()
            if (alpha > 0f) drawRect(color = color.copy(alpha = 0.18f * alpha))
        }
}
