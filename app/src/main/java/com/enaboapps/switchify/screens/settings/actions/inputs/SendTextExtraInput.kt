package com.enaboapps.switchify.screens.settings.actions.inputs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.TextArea
import com.enaboapps.switchify.service.custom.actions.store.data.ActionExtra

@Composable
fun SendTextExtraInput(
    selectedExtra: ActionExtra?,
    onExtraUpdated: (ActionExtra?) -> Unit,
    onExtraValidated: (Boolean) -> Unit
) {
    Column {
        TextArea(
            value = selectedExtra?.numberToSend ?: "",
            onValueChange = { text ->
                onExtraUpdated(
                    ActionExtra(
                        numberToSend = text,
                        message = selectedExtra?.message ?: ""
                    )
                )
                val isValid = text.isNotBlank() && text.matches(Regex("^\\d+$"))
                onExtraValidated(isValid)
            },
            labelResId = R.string.action_number_to_text,
            isError = selectedExtra?.numberToSend?.isBlank() == true,
            supportingTextResId = R.string.action_number_to_text_desc
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextArea(
            value = selectedExtra?.message ?: "",
            onValueChange = { text ->
                onExtraUpdated(
                    ActionExtra(
                        numberToSend = selectedExtra?.numberToSend ?: "",
                        message = text
                    )
                )
                onExtraValidated(true)
            },
            labelResId = R.string.action_message
        )
    }
}
