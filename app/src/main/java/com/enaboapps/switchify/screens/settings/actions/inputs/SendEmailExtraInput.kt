package com.enaboapps.switchify.screens.settings.actions.inputs

import android.util.Patterns
import androidx.compose.runtime.Composable
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.TextArea
import com.enaboapps.switchify.service.custom.actions.store.data.ActionExtra

@Composable
fun SendEmailExtraInput(
    selectedExtra: ActionExtra?,
    onExtraUpdated: (ActionExtra?) -> Unit,
    onExtraValidated: (Boolean) -> Unit
) {
    TextArea(
        value = selectedExtra?.emailAddress ?: "",
        onValueChange = { text ->
            onExtraUpdated(
                ActionExtra(
                    emailAddress = text
                )
            )

            val isValid = Patterns.EMAIL_ADDRESS.matcher(text).matches()
            onExtraValidated(isValid)
        },
        labelResId = R.string.action_email_address,
        isError = selectedExtra?.emailAddress?.isBlank() == true,
        supportingTextResId = R.string.action_email_address_desc
    )
}
