package com.enaboapps.switchify.screens.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.theme.Dimens
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.Picker

@Composable
fun WelcomeStep(
    isNewUser: Boolean?,
    onNewUserChoice: (Boolean) -> Unit
) {
    var selectedOption by remember { mutableStateOf(isNewUser) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spaceL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Welcome message
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = Dimens.spaceXs)
        )

        Text(
            text = stringResource(R.string.onboarding_welcome_subtitle),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Dimens.spaceL + Dimens.spaceM)
        )

        // User selection using Picker
        val userOptions = listOf(true, false)
        val newUserText = stringResource(R.string.onboarding_new_user)
        val returningUserText = stringResource(R.string.onboarding_returning_user)
        val newUserDesc = stringResource(R.string.onboarding_new_user_desc)
        val returningUserDesc = stringResource(R.string.onboarding_returning_user_desc)

        Picker(
            titleResId = R.string.onboarding_are_you_new,
            selectedItem = selectedOption,
            items = userOptions,
            onItemSelected = { selectedOption = it },
            itemToString = { isNew ->
                if (isNew) newUserText else returningUserText
            },
            itemDescription = { isNew ->
                if (isNew) newUserDesc else returningUserDesc
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        // Continue button
        ActionButton(
            textResId = R.string.onboarding_continue,
            enabled = selectedOption != null,
            onClick = {
                selectedOption?.let { onNewUserChoice(it) }
            }
        )
    }
}
