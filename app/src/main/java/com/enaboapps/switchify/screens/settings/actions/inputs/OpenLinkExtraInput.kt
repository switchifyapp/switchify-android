package com.enaboapps.switchify.screens.settings.actions.inputs

import androidx.compose.runtime.Composable
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.TextArea
import com.enaboapps.switchify.service.actions.custom.store.data.ActionExtra

@Composable
fun OpenLinkExtraInput(
    selectedExtra: ActionExtra?,
    onExtraUpdated: (ActionExtra?) -> Unit,
    onExtraValidated: (Boolean) -> Unit
) {
    TextArea(
        value = selectedExtra?.linkUrl ?: "",
        onValueChange = { text ->
            onExtraUpdated(
                ActionExtra(
                    linkUrl = text
                )
            )
            val isValid = text.isNotBlank() && text.matches(Regex("^(http|https)://.*$"))
            onExtraValidated(isValid)
        },
        labelResId = R.string.action_link_url,
        isError = selectedExtra?.linkUrl?.isBlank() == true,
        supportingTextResId = R.string.action_link_url_desc
    )
}
