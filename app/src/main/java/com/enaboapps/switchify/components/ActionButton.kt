package com.enaboapps.switchify.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Button type enum to define the visual hierarchy and semantic meaning of action buttons
 */
enum class ActionButtonType {
    /**
     * Primary action button - filled style with primary colors
     * Use for main actions like "Save", "Continue", "Sign In", etc.
     */
    PRIMARY,

    /**
     * Secondary action button - tonal style with primary content
     * Use for secondary actions like "Cancel", "Skip", "Back", etc.
     */
    SECONDARY,

    /**
     * Destructive action button - tonal style with error container colors
     * Use for destructive actions like "Delete", "Remove", "Clear", etc.
     */
    DESTRUCTIVE
}

/**
 * Action button component that supports different button types for proper UI hierarchy.
 *
 * This component provides better semantic clarity and built-in
 * support for primary, secondary, and destructive button variants.
 *
 * @param textResId The string resource ID for the button text
 * @param onClick Callback invoked when the button is clicked
 * @param modifier Modifier to be applied to the button container
 * @param type The type of button determining its visual style and semantic meaning
 * @param enabled Whether the button is enabled and clickable
 *
 * Usage examples:
 * ```
 * // Primary action (default)
 * ActionButton(
 *     textResId = R.string.button_save,
 *     onClick = { save() }
 * )
 *
 * // Secondary action
 * ActionButton(
 *     textResId = R.string.button_cancel,
 *     onClick = { cancel() },
 *     type = ActionButtonType.SECONDARY
 * )
 *
 * // Destructive action
 * ActionButton(
 *     textResId = R.string.button_delete_account,
 *     onClick = { deleteAccount() },
 *     type = ActionButtonType.DESTRUCTIVE
 * )
 * ```
 */
@Composable
fun ActionButton(
    textResId: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    type: ActionButtonType = ActionButtonType.PRIMARY,
    enabled: Boolean = true,
    applyPadding: Boolean = true,
    leadingIcon: ImageVector? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding
) {
    val buttonModifier = if (applyPadding) {
        modifier.padding(16.dp)
    } else {
        modifier
    }

    when (type) {
        ActionButtonType.PRIMARY -> {
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = buttonModifier,
                contentPadding = contentPadding
            ) {
                if (leadingIcon != null) {
                    androidx.compose.material3.Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text(text = stringResource(textResId))
            }
        }

        ActionButtonType.SECONDARY -> {
            FilledTonalButton(
                onClick = onClick,
                enabled = enabled,
                modifier = buttonModifier,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = contentPadding
            ) {
                if (leadingIcon != null) {
                    androidx.compose.material3.Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text(text = stringResource(textResId))
            }
        }

        ActionButtonType.DESTRUCTIVE -> {
            FilledTonalButton(
                onClick = onClick,
                enabled = enabled,
                modifier = buttonModifier,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                contentPadding = contentPadding
            ) {
                if (leadingIcon != null) {
                    androidx.compose.material3.Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text(text = stringResource(textResId))
            }
        }
    }
}
