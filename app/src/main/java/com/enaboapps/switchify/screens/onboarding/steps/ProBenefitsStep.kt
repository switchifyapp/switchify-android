package com.enaboapps.switchify.screens.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.enaboapps.switchify.components.ActionButtonType
import com.enaboapps.switchify.components.Panel
import com.enaboapps.switchify.theme.Dimens

/**
 * Onboarding step that showcases Pro benefits to new users.
 * Displayed after the accessibility service setup, before practice.
 * Non-blocking: users can always choose to continue without upgrading.
 */
@Composable
fun ProBenefitsStep(
    onLearnMore: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spaceL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Star icon
        Icon(
            imageVector = Icons.Rounded.Star,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(Dimens.spaceM))

        // Title
        Text(
            text = stringResource(R.string.onboarding_pro_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = Dimens.spaceXs)
        )

        // Subtitle
        Text(
            text = stringResource(R.string.onboarding_pro_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Dimens.spaceL)
        )

        // Benefits card
        Panel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spaceM)
        ) {
            Column(
                modifier = Modifier.padding(Dimens.spaceM)
            ) {
                BenefitItem(text = stringResource(R.string.onboarding_pro_benefit_unlimited))
                Spacer(modifier = Modifier.height(Dimens.spaceS))
                BenefitItem(text = stringResource(R.string.onboarding_pro_benefit_no_trial))
                Spacer(modifier = Modifier.height(Dimens.spaceS))
                BenefitItem(text = stringResource(R.string.onboarding_pro_benefit_support))
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Action buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)
        ) {
            ActionButton(
                textResId = R.string.onboarding_pro_learn_more,
                onClick = onLearnMore
            )

            ActionButton(
                textResId = R.string.onboarding_pro_maybe_later,
                onClick = onContinue,
                type = ActionButtonType.SECONDARY
            )
        }
    }
}

@Composable
private fun BenefitItem(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)
    ) {
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
