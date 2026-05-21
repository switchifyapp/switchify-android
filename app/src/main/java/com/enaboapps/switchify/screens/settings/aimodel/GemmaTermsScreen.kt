package com.enaboapps.switchify.screens.settings.aimodel

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.ActionButtonType
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.InfoCard
import com.enaboapps.switchify.service.llm.model.AiModelConfig
import com.enaboapps.switchify.theme.Dimens

@Composable
fun GemmaTermsScreen(navController: NavController) {
    val context = LocalContext.current
    val preferenceManager = remember { PreferenceManager(context) }
    val alreadyAccepted = remember {
        preferenceManager.getBooleanValue(
            PreferenceManager.PREFERENCE_KEY_GEMMA_TERMS_ACCEPTED
        )
    }

    BaseView(
        titleResId = R.string.screen_title_gemma_terms,
        navController = navController
    ) {
        InfoCard(
            titleResId = R.string.gemma_terms_intro_title,
            descriptionResId = R.string.gemma_terms_intro
        )
        Spacer(modifier = Modifier.height(Dimens.spaceM))
        ActionButton(
            textResId = R.string.gemma_terms_view_terms,
            onClick = { openUrl(context, AiModelConfig.GEMMA_TERMS_URL) },
            type = ActionButtonType.SECONDARY
        )
        ActionButton(
            textResId = R.string.gemma_terms_view_policy,
            onClick = { openUrl(context, AiModelConfig.GEMMA_USE_POLICY_URL) },
            type = ActionButtonType.SECONDARY
        )
        if (!alreadyAccepted) {
            Spacer(modifier = Modifier.height(Dimens.spaceL))
            ActionButton(
                textResId = R.string.gemma_terms_accept,
                onClick = {
                    preferenceManager.setBooleanValue(
                        PreferenceManager.PREFERENCE_KEY_GEMMA_TERMS_ACCEPTED,
                        true
                    )
                    navController.popBackStack()
                }
            )
        }
    }
}

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }
}
