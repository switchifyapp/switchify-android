package com.enaboapps.switchify.screens.pc.remote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import kotlinx.coroutines.launch

object PcScrollToTop {
    val CLEARANCE = 56.dp
}

@Composable
fun PcScrollToTopButton(scrollState: ScrollState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var scrolling by remember { mutableStateOf(false) }
    val visible by remember(scrollState) { derivedStateOf { scrollState.value > 0 } }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        SmallFloatingActionButton(
            onClick = {
                if (scrolling) return@SmallFloatingActionButton
                scrolling = true
                scope.launch {
                    try {
                        scrollState.animateScrollTo(0)
                    } finally {
                        scrolling = false
                    }
                }
            }
        ) {
            Icon(imageVector = Icons.Rounded.VerticalAlignTop, contentDescription = stringResource(R.string.pc_remote_scroll_to_top))
        }
    }
}
