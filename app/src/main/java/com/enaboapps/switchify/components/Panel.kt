package com.enaboapps.switchify.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.activities.ui.theme.SwitchifyTheme

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    containerColor: Color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    if (onClick != null) {
        val interactionSource = remember { MutableInteractionSource() }
        val animatedColor = animatedPressContainerColor(
            interactionSource = interactionSource,
            idleColor = containerColor,
            pressedColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
        Card(
            modifier = modifier
                .springPressScale(interactionSource)
                .clickable(
                    role = Role.Button,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = animatedColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            content()
        }
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            content()
        }
    }
}

@Preview(showBackground = true, name = "Panel — default surface")
@Composable
private fun PanelPreview() {
    SwitchifyTheme {
        Panel(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "Panel content",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(20.dp)
            )
        }
    }
}

@Preview(showBackground = true, name = "Panel — semantic primary container")
@Composable
private fun PanelSemanticPreview() {
    SwitchifyTheme {
        Panel(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = "Semantic panel (primaryContainer)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(20.dp)
            )
        }
    }
}
