package com.enaboapps.switchify.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Adaptive stack component that switches between vertical (Column) and horizontal (Row) 
 * layouts based on device type. Uses vertical layout for phones and horizontal for tablets.
 * Perfect for button groups and other UI elements that need responsive behavior.
 * 
 * @param modifier Modifier to be applied to the container
 * @param spacing Space between items in the stack
 * @param horizontalAlignment Horizontal alignment when in vertical mode (Column)
 * @param verticalAlignment Vertical alignment when in horizontal mode (Row)
 * @param content The content to be laid out adaptively
 * 
 * Usage examples:
 * ```
 * // Basic usage - vertical on phone, horizontal on tablet
 * AdaptiveStack {
 *     ActionButton(text = "Save", onClick = { save() }, modifier = Modifier.adaptiveFill())
 *     ActionButton(text = "Cancel", onClick = { cancel() }, modifier = Modifier.adaptiveFill())
 * }
 * ```
 */
@Composable
fun AdaptiveStack(
    modifier: Modifier = Modifier,
    spacing: Dp = 16.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable AdaptiveStackScope.() -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600 && configuration.smallestScreenWidthDp >= 600
    val isPhone = !isTablet
    
    if (isPhone) {
        // Vertical layout for phones
        Column(
            modifier = modifier,
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            val scope = AdaptiveStackScope(true, this, null)
            scope.content()
        }
    } else {
        // Horizontal layout for tablets
        Row(
            modifier = modifier,
            verticalAlignment = verticalAlignment,
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            val scope = AdaptiveStackScope(false, null, this)
            scope.content()
        }
    }
}

/**
 * Scope class for AdaptiveStack that provides information about the current layout mode
 * and convenient modifiers for adaptive behavior.
 */
class AdaptiveStackScope(
    val isPhone: Boolean,
    private val columnScope: ColumnScope?,
    private val rowScope: RowScope?
) {
    val isTablet: Boolean get() = !isPhone
    
    /**
     * Extension function that applies fillMaxWidth() when on phone (vertical mode),
     * and weight(1f) when on tablet (horizontal mode).
     * Perfect for buttons that should expand to fill available space.
     */
    fun Modifier.adaptiveFill(): Modifier = if (isPhone) {
        this.fillMaxWidth()
    } else {
        with(rowScope!!) {
            this@adaptiveFill.weight(1f)
        }
    }
    
    /**
     * Modifier that applies different modifiers based on device type.
     * 
     * @param phoneModifier Modifier to apply on phones (vertical layout)
     * @param tabletModifier Modifier to apply on tablets (horizontal layout)
     */
    fun Modifier.adaptiveModifier(
        phoneModifier: Modifier = Modifier,
        tabletModifier: Modifier = Modifier
    ): Modifier = this.then(
        if (isPhone) phoneModifier else tabletModifier
    )
}