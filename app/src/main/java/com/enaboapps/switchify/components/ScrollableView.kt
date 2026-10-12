package com.enaboapps.switchify.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.enaboapps.switchify.theme.Dimens

@Composable
fun ScrollableView(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(Dimens.spaceM),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        content()
    }
}
