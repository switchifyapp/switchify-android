package com.enaboapps.switchify.screens.replydrafter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.ActionButtonType
import com.enaboapps.switchify.theme.Dimens

@Composable
fun ReplyDrafterScreen(
    state: ReplyDrafterUiState,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(Dimens.spaceL)
        ) {
            Text(
                text = stringResource(R.string.reply_drafter_suggestions_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(Dimens.spaceL))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (state) {
                    is ReplyDrafterUiState.Loading -> LoadingContent()

                    is ReplyDrafterUiState.Suggestions ->
                        SuggestionsContent(state.replies, onSelect)

                    is ReplyDrafterUiState.Failed ->
                        FailedContent(state, onRetry)
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spaceM))
            ActionButton(
                textResId = R.string.reply_drafter_close,
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
                type = ActionButtonType.SECONDARY,
                applyPadding = false
            )
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
            text = stringResource(R.string.reply_drafter_processing),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun SuggestionsContent(replies: List<String>, onSelect: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)
    ) {
        Text(
            text = stringResource(R.string.reply_drafter_pick_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        replies.forEach { reply ->
            ReplyCard(reply = reply, onClick = { onSelect(reply) })
        }
    }
}

@Composable
private fun ReplyCard(reply: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = reply,
            modifier = Modifier.padding(Dimens.spaceM),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun FailedContent(state: ReplyDrafterUiState.Failed, onRetry: () -> Unit) {
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
                textResId = R.string.reply_drafter_retry,
                onClick = onRetry
            )
        }
    }
}
