package com.enaboapps.switchify.screens.settings.actions.inputs

import androidx.compose.runtime.Composable
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.TextArea
import com.enaboapps.switchify.service.actions.custom.store.data.ActionExtra

@Composable
fun CallNumberExtraInput(
    selectedExtra: ActionExtra?,
    onExtraUpdated: (ActionExtra?) -> Unit,
    onExtraValidated: (Boolean) -> Unit
) {
    TextArea(
        value = selectedExtra?.numberToCall ?: "",
        onValueChange = { text ->
            onExtraUpdated(
                ActionExtra(
                    numberToCall = text
                )
            )
            val isValid = text.isNotBlank() && text.matches(Regex("^\\d+$"))
            onExtraValidated(isValid)
        },
        labelResId = R.string.action_number,
        isError = selectedExtra?.numberToCall?.isBlank() == true,
        supportingTextResId = R.string.action_number_desc
    )
}
