package com.enaboapps.switchify.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

data class NavBarAction(
    val textResId: Int? = null,
    val icon: ImageVector? = null,
    val contentDescription: String? = null,
    val onClick: () -> Unit
)

@Composable
fun NavBar(
    title: String,
    navController: NavController,
    actions: List<NavBarAction> = emptyList(),
    showBackButton: Boolean? = null,
    onBackPressed: (() -> Unit)? = null
) {
    val canGoBack = showBackButton ?: (navController.previousBackStackEntry != null)
    val primaryColor = MaterialTheme.colorScheme.primary
    val gradientColors = listOf(
        primaryColor,
        Color(primaryColor.red * 0.6f, primaryColor.green * 0.6f, primaryColor.blue * 0.6f)
    )

    Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = gradientColors
                    )
                )
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canGoBack) {
                    IconButton(onClick = {
                        onBackPressed?.invoke() ?: navController.popBackStack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if (canGoBack) 0.dp else 16.dp)
                )

                actions.forEach { action ->
                    if (action.icon != null) {
                        IconButton(onClick = action.onClick) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = action.contentDescription,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    } else if (action.textResId != null) {
                        TextButton(
                            onClick = action.onClick,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(stringResource(action.textResId))
                        }
                    }
                }
            }
        }
    }
}