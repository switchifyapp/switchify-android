package com.enaboapps.switchify.screens.tips

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.InfoCard
import com.enaboapps.switchify.components.Panel
import com.enaboapps.switchify.theme.Dimens
import com.enaboapps.switchify.tips.TipMessage
import com.enaboapps.switchify.tips.TipPreferences

@Composable
fun TipScreen(navController: NavController, tipKey: String) {
    val context = LocalContext.current
    val tip = remember(tipKey) { TipPreferences(context).tip(tipKey) }

    BaseView(
        titleResId = R.string.screen_title_tip,
        navController = navController
    ) {
        if (tip == null) {
            InfoCard(
                titleResId = R.string.tip_unavailable_title,
                descriptionResId = R.string.tip_unavailable_description
            )
        } else {
            TipCard(tip)
            tip.url?.let { url ->
                ActionButton(
                    textResId = R.string.tip_learn_more,
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(Dimens.spaceM))
            Text(
                text = stringResource(R.string.tip_turn_off_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TipCard(tip: TipMessage) {
    Panel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Dimens.spaceL)) {
            Box(
                modifier = Modifier
                    .size(Dimens.heroIconSize)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_stat_tip),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(Dimens.spaceM))
            Text(
                text = tip.title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(Dimens.spaceS))
            Text(
                text = tip.body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
