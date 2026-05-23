package com.enaboapps.switchify.screens.screenhighlights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.NavBar
import com.enaboapps.switchify.service.llm.ExtractedItem
import com.enaboapps.switchify.service.llm.HighlightType
import com.enaboapps.switchify.theme.Dimens

@Composable
fun ScreenHighlightsScreen(
    state: ScreenHighlightsUiState,
    onSelect: (ExtractedItem) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    Scaffold(
        topBar = {
            NavBar(
                title = stringResource(R.string.screen_highlights_title),
                showBackButton = true,
                onBackPressed = onClose
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Dimens.spaceL)
            ) {
                when (state) {
                    is ScreenHighlightsUiState.Loading -> LoadingContent()

                    is ScreenHighlightsUiState.Items ->
                        ItemsContent(state.items, onSelect)

                    is ScreenHighlightsUiState.Empty ->
                        EmptyContent(onRetry)

                    is ScreenHighlightsUiState.Failed ->
                        FailedContent(state, onRetry)
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(Dimens.spaceM))
        Text(
            text = stringResource(R.string.screen_highlights_processing),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun ItemsContent(items: List<ExtractedItem>, onSelect: (ExtractedItem) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)
    ) {
        Text(
            text = stringResource(R.string.screen_highlights_pick_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        items.forEach { item ->
            HighlightCard(item = item, onClick = { onSelect(item) })
        }
    }
}

@Composable
private fun HighlightCard(item: ExtractedItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Dimens.spaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)
        ) {
            TypeChip(item.type)
            Text(
                text = item.value,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun TypeChip(type: HighlightType) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = stringResource(typeLabelRes(type)),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = Dimens.spaceXs, vertical = 4.dp)
        )
    }
}

private fun typeLabelRes(type: HighlightType): Int = when (type) {
    HighlightType.URL -> R.string.screen_highlights_type_url
    HighlightType.PHONE -> R.string.screen_highlights_type_phone
    HighlightType.EMAIL -> R.string.screen_highlights_type_email
    HighlightType.DATE -> R.string.screen_highlights_type_date
    HighlightType.ADDRESS -> R.string.screen_highlights_type_address
    HighlightType.OTHER -> R.string.screen_highlights_type_other
}

@Composable
private fun EmptyContent(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.screen_highlights_none_found),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Dimens.spaceM))
        ActionButton(
            textResId = R.string.screen_highlights_retry,
            onClick = onRetry
        )
    }
}

@Composable
private fun FailedContent(state: ScreenHighlightsUiState.Failed, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(state.messageRes),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        if (state.canRetry) {
            Spacer(modifier = Modifier.height(Dimens.spaceM))
            ActionButton(
                textResId = R.string.screen_highlights_retry,
                onClick = onRetry
            )
        }
    }
}
