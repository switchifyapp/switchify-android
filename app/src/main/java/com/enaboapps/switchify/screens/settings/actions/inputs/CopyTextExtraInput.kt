package com.enaboapps.switchify.screens.settings.actions.inputs

import androidx.compose.runtime.Composable
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.TextArea
import com.enaboapps.switchify.service.custom.actions.store.data.ActionExtra

@Composable
fun CopyTextExtraInput(
    selectedExtra: ActionExtra?,
    onExtraUpdated: (ActionExtra?) -> Unit,
    onExtraValidated: (Boolean) -> Unit
) {
    TextArea(
        value = selectedExtra?.textToCopy ?: "",
        onValueChange = { text ->
            onExtraUpdated(
                ActionExtra(
                    textToCopy = text
                )
            )

            onExtraValidated(text.isNotBlank())
        },
        labelResId = R.string.action_text_to_copy,
        isError = selectedExtra?.textToCopy?.isBlank() == true,
        supportingTextResId = R.string.action_text_to_copy_desc
    )
}
