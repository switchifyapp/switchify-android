package com.enaboapps.switchify.screens.settings.switches

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.FullWidthButton
import com.enaboapps.switchify.components.Picker
import com.enaboapps.switchify.components.PreferenceValueSelector
import com.enaboapps.switchify.components.PreferenceTimeStepper
import com.enaboapps.switchify.components.TextArea
import com.enaboapps.switchify.screens.settings.switches.actions.SwitchActionPicker
import com.enaboapps.switchify.screens.settings.switches.models.AddEditCameraSwitchScreenModel
import com.enaboapps.switchify.service.switches.camera.CameraSwitchManager
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture
import kotlinx.coroutines.launch

@Composable
fun AddEditCameraSwitchScreen(navController: NavController, code: String? = null) {
    val viewModel = remember { AddEditCameraSwitchScreenModel().apply { init(code) } }

    BaseView(
        titleResId = if (code == null) R.string.screen_title_add_switch else R.string.screen_title_edit_switch,
        navController = navController
    ) {
        MainContent(code, viewModel, navController)
    }
}

@Composable
private fun MainContent(
    code: String?,
    viewModel: AddEditCameraSwitchScreenModel,
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Switch Name
        SwitchName(
            name = viewModel.name,
            onNameChange = { viewModel.updateName(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (code == null) {
            // Facial Gesture Selection
            val gestures = listOf(
                CameraSwitchFacialGesture(CameraSwitchFacialGesture.SMILE),
                CameraSwitchFacialGesture(CameraSwitchFacialGesture.LEFT_WINK),
                CameraSwitchFacialGesture(CameraSwitchFacialGesture.RIGHT_WINK),
                CameraSwitchFacialGesture(CameraSwitchFacialGesture.BLINK),
                CameraSwitchFacialGesture(CameraSwitchFacialGesture.HEAD_TURN_LEFT),
                CameraSwitchFacialGesture(CameraSwitchFacialGesture.HEAD_TURN_RIGHT),
                CameraSwitchFacialGesture(CameraSwitchFacialGesture.HEAD_TURN_UP),
                CameraSwitchFacialGesture(CameraSwitchFacialGesture.HEAD_TURN_DOWN)
            )

            Picker(
                titleResId = R.string.section_title_facial_gesture,
                selectedItem = viewModel.selectedGesture.value,
                items = gestures,
                onItemSelected = { gesture ->
                    viewModel.setGesture(gesture)
                },
                itemToString = { it.getName() },
                itemDescription = { it.getDescription() }
            )
        } else {
            Text(
                text = "This switch is already set up with ${viewModel.selectedGesture.value?.getName()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Selection
        SwitchActionPicker(
            titleResId = R.string.section_title_action,
            switchAction = viewModel.action.value,
            onChange = { viewModel.setAction(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Show different settings based on gesture type
        viewModel.selectedGesture.value?.let { gesture ->
            if (gesture.isHeadTurn()) {
                // Head Turn Sensitivity (only for head turn gestures)
                PreferenceValueSelector(
                    value = viewModel.sensitivity.intValue,
                    titleResId = R.string.preference_title_head_turn_sensitivity,
                    summaryResId = R.string.preference_summary_head_turn_sensitivity,
                    min = 1,
                    max = 10,
                    displayFormatter = { sensitivity ->
                        "${CameraSwitchManager.getHeadTurnThreshold(sensitivity).toInt()}°"
                    },
                    onValueChanged = { newValue ->
                        viewModel.setSensitivity(newValue)
                    }
                )
            } else {
                // Facial Gesture Time (only for non-head turn gestures)
                PreferenceTimeStepper(
                    value = viewModel.facialGestureTime.longValue,
                    titleResId = R.string.preference_title_facial_gesture_time,
                    summaryResId = R.string.preference_summary_facial_gesture_time,
                    min = 100,
                    max = 10000,
                    step = 100,
                    onValueChanged = { newValue ->
                        viewModel.setFacialGestureTime(newValue)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Save Button
        FullWidthButton(
            textResId = R.string.button_save,
            enabled = viewModel.isValid.value,
            onClick = {
                if (code == null) {
                    // Check Pro status for new switches
                    if (IAPHandler.hasPurchasedPro()) {
                        viewModel.save(context) { success ->
                            scope.launch {
                                if (success) {
                                    navController.popBackStack()
                                } else {
                                    Toast.makeText(context, "Error saving switch", Toast.LENGTH_SHORT)
                                        .show()
                                }
                            }
                        }
                    } else {
                        // Show Pro feature message
                        Toast.makeText(
                            context,
                            context.getString(R.string.pro_feature_message),
                            Toast.LENGTH_LONG
                        ).show()
                        
                        // Open Pro upgrade screen
                        navController.navigate(NavigationRoute.Paywall.name)
                    }
                } else {
                    // Allow editing existing switches
                    viewModel.save(context) { success ->
                        scope.launch {
                            if (success) {
                                navController.popBackStack()
                            } else {
                                Toast.makeText(context, "Error saving switch", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    }
                }
            }
        )

        // Delete Button (only show when editing)
        if (code != null) {
            Spacer(modifier = Modifier.height(8.dp))
            FullWidthButton(
                textResId = R.string.button_delete,
                onClick = { viewModel.showDeleteConfirmation.value = true }
            )
        }
    }

    // Delete confirmation dialog
    if (viewModel.showDeleteConfirmation.value) {
        AlertDialog(
            onDismissRequest = { viewModel.showDeleteConfirmation.value = false },
            title = { Text(stringResource(R.string.dialog_title_delete)) },
            text = { Text(stringResource(R.string.dialog_message_delete)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.showDeleteConfirmation.value = false
                        viewModel.delete(context) { success ->
                            scope.launch {
                                if (success) {
                                    navController.popBackStack()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Error deleting switch",
                                        Toast.LENGTH_SHORT
                                    )
                                        .show()
                                }
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.button_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.showDeleteConfirmation.value = false }
                ) {
                    Text(stringResource(R.string.button_cancel))
                }
            }
        )
    }
}