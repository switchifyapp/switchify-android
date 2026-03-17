package com.enaboapps.switchify.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.auth.repository.AuthRepository
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.Picker
import com.enaboapps.switchify.components.ScrollableView
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserFeedbackScreen(navController: NavController) {
    var feedbackText by remember { mutableStateOf("") }
    var contactEmail by remember { mutableStateOf("") }
    var feedbackType by remember { mutableStateOf(FeedbackType.SUGGESTION) }
    var isSubmitting by remember { mutableStateOf(false) }
    var showSuccessMessage by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val feedbackErrorEmpty = stringResource(R.string.feedback_error_empty)

    // Auto-fill email if user is signed in
    LaunchedEffect(Unit) {
        val authRepository = AuthRepository.instance
        if (authRepository.isUserSignedIn()) {
            authRepository.getCurrentUser()?.let { user ->
                user.email?.let { email ->
                    contactEmail = email
                }
            }
        }
    }

    BaseView(
        titleResId = R.string.screen_title_feedback,
        navController = navController,
        enableScroll = false
    ) {
        ScrollableView {
            if (showSuccessMessage) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.feedback_success_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.feedback_success_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        ActionButton(
                            textResId = R.string.button_done,
                            onClick = {
                                navController.popBackStack()
                            })
                    }
                }
            } else {
                Picker(
                    titleResId = R.string.feedback_type_section,
                    selectedItem = feedbackType,
                    items = FeedbackType.entries,
                    onItemSelected = { feedbackType = it },
                    itemToString = { getFeedbackTypeTitle(it, context) },
                    itemDescription = { getDescriptionForFeedbackType(it, context) }
                )

                Section(titleResId = R.string.feedback_details_section) {
                    OutlinedTextField(
                        value = feedbackText,
                        onValueChange = {
                            feedbackText = it
                            errorMessage = null
                        },
                        label = { Text(stringResource(R.string.feedback_text_label)) },
                        placeholder = { Text(stringResource(R.string.feedback_text_placeholder)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .padding(horizontal = 16.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.clearFocus() }
                        ),
                        maxLines = 5,
                        singleLine = false,
                        isError = errorMessage != null
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = contactEmail,
                        onValueChange = { contactEmail = it },
                        label = { Text(stringResource(R.string.feedback_email_label)) },
                        placeholder = { Text(stringResource(R.string.feedback_email_placeholder)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        ),
                        singleLine = true
                    )
                }

                if (errorMessage != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                ActionButton(
                    textResId = R.string.feedback_submit,
                    onClick = {
                        if (feedbackText.trim().isBlank()) {
                            errorMessage = feedbackErrorEmpty
                            return@ActionButton
                        }

                        isSubmitting = true
                        submitFeedback(
                            feedbackText = feedbackText.trim(),
                            contactEmail = contactEmail.trim(),
                            feedbackType = feedbackType,
                            context = context,
                            onSuccess = {
                                isSubmitting = false
                                showSuccessMessage = true
                            },
                            onError = { error ->
                                isSubmitting = false
                                errorMessage = error
                            }
                        )
                    },
                    enabled = !isSubmitting && feedbackText.trim().isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }
    }
}

private fun submitFeedback(
    feedbackText: String,
    contactEmail: String,
    feedbackType: FeedbackType,
    context: android.content.Context,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    try {
        Logger.log(
            event = LogEvent.UserFeedbackSubmitted,
            data = buildMap {
                put("feedback_type", feedbackType.name.lowercase())
                put("has_contact_email", contactEmail.isNotBlank())
                put("feedback_text", feedbackText)
                if (contactEmail.isNotBlank()) put("contact_email", contactEmail)
                put("is_bug_report", feedbackType == FeedbackType.BUG_REPORT)
            }
        )
        onSuccess()
    } catch (e: Exception) {
        Logger.log(
            event = LogEvent.UserFeedbackSubmissionError,
            data = mapOf("component" to "UserFeedbackScreen"),
            throwable = e
        )
        onError(context.getString(R.string.feedback_error_submit_failed))
    }
}

private fun getFeedbackTypeTitle(type: FeedbackType, context: android.content.Context): String {
    return context.getString(type.titleResId)
}

private fun getDescriptionForFeedbackType(
    type: FeedbackType,
    context: android.content.Context
): String {
    return when (type) {
        FeedbackType.BUG_REPORT -> context.getString(R.string.feedback_type_bug_description)
        FeedbackType.FEATURE_REQUEST -> context.getString(R.string.feedback_type_feature_description)
        FeedbackType.SUGGESTION -> context.getString(R.string.feedback_type_suggestion_description)
        FeedbackType.OTHER -> context.getString(R.string.feedback_type_other_description)
    }
}

enum class FeedbackType(val titleResId: Int) {
    BUG_REPORT(R.string.feedback_type_bug_report),
    FEATURE_REQUEST(R.string.feedback_type_feature_request),
    SUGGESTION(R.string.feedback_type_suggestion),
    OTHER(R.string.feedback_type_other)
}