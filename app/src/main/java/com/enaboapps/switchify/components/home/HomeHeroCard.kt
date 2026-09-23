package com.enaboapps.switchify.components.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.springPressScale
import com.enaboapps.switchify.theme.Dimens

@Composable
fun HomeHeroCard(
    isAccessibilityServiceEnabled: Boolean,
    activeProfileName: String,
    isConfigValid: Boolean,
    onEnableService: () -> Unit,
    onOpenProfiles: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large
) {
    val scheme = MaterialTheme.colorScheme
    val baseTopColor = scheme.surfaceColorAtElevation(3.dp)
    val baseBottomColor = scheme.surfaceColorAtElevation(1.dp)
    val errorTint = scheme.errorContainer.copy(alpha = 0.18f)

    val animatedTop by animateColorAsState(
        targetValue = if (isAccessibilityServiceEnabled) baseTopColor else errorTint.compositeOver(baseTopColor),
        animationSpec = tween(280),
        label = "heroTop"
    )
    val animatedBottom by animateColorAsState(
        targetValue = baseBottomColor,
        animationSpec = tween(280),
        label = "heroBottom"
    )

    val pipColor by animateColorAsState(
        targetValue = if (isAccessibilityServiceEnabled) scheme.secondary else scheme.error,
        animationSpec = tween(280),
        label = "heroPip"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val cardModifier = modifier
        .fillMaxWidth()
        .springPressScale(interactionSource)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClick = if (isAccessibilityServiceEnabled) onOpenProfiles else onEnableService
        )
        .semantics(mergeDescendants = true) {}

    Card(
        modifier = cardModifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(animatedTop, animatedBottom)
                    )
                )
                .padding(horizontal = Dimens.spaceL, vertical = 28.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXs)
                ) {
                    Box(
                        modifier = Modifier
                            .size(Dimens.heroPipSize)
                            .clip(CircleShape)
                            .background(pipColor)
                    )
                    Text(
                        text = stringResource(R.string.home_hero_eyebrow),
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant
                    )
                }

                Crossfade(
                    targetState = isAccessibilityServiceEnabled,
                    animationSpec = tween(280),
                    label = "heroState"
                ) { enabled ->
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
                        Text(
                            text = stringResource(
                                if (enabled) R.string.home_hero_running_title
                                else R.string.home_hero_disabled_title
                            ),
                            style = MaterialTheme.typography.displaySmall,
                            color = if (enabled) scheme.onSurface else scheme.error
                        )
                        Text(
                            text = supportingLine(
                                resolveHomeHeroStatus(enabled, isConfigValid, activeProfileName)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant
                        )
                        if (!enabled) {
                            Box(modifier = Modifier.padding(top = Dimens.spaceM)) {
                                Button(onClick = onEnableService) {
                                    Text(stringResource(R.string.home_hero_action_open_settings))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun supportingLine(
    status: HomeHeroStatus
): String {
    return when (status) {
        HomeHeroStatus.Disabled -> stringResource(R.string.home_hero_disabled_summary)
        is HomeHeroStatus.Active -> stringResource(
            R.string.home_hero_active_profile,
            status.profileName
        )
        is HomeHeroStatus.ActiveNeedsAttention -> stringResource(
            R.string.home_hero_active_profile_needs_attention,
            status.profileName
        )
    }
}

internal sealed interface HomeHeroStatus {
    data object Disabled : HomeHeroStatus
    data class Active(val profileName: String) : HomeHeroStatus
    data class ActiveNeedsAttention(val profileName: String) : HomeHeroStatus
}

internal fun resolveHomeHeroStatus(
    isAccessibilityServiceEnabled: Boolean,
    isConfigValid: Boolean,
    activeProfileName: String
): HomeHeroStatus {
    if (!isAccessibilityServiceEnabled) return HomeHeroStatus.Disabled
    return if (isConfigValid) {
        HomeHeroStatus.Active(activeProfileName)
    } else {
        HomeHeroStatus.ActiveNeedsAttention(activeProfileName)
    }
}

private fun Color.compositeOver(background: Color): Color {
    val a = alpha + background.alpha * (1f - alpha)
    if (a == 0f) return Color.Transparent
    val r = (red * alpha + background.red * background.alpha * (1f - alpha)) / a
    val g = (green * alpha + background.green * background.alpha * (1f - alpha)) / a
    val b = (blue * alpha + background.blue * background.alpha * (1f - alpha)) / a
    return Color(r, g, b, a)
}
