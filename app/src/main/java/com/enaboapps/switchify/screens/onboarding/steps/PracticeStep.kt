package com.enaboapps.switchify.screens.onboarding.steps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.utils.ServiceUtils
import com.enaboapps.switchify.utils.Resources
import kotlinx.coroutines.delay
import kotlin.random.Random

data class PracticeBox(
    val id: Int,
    val icon: ImageVector,
    val label: String,
    val color: Color,
    var isActivated: Boolean = false
)

@Composable
fun PracticeStep(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val serviceUtils = remember { ServiceUtils() }
    val isAccessibilityServiceEnabled = remember { serviceUtils.isAccessibilityServiceEnabled(context) }
    
    var activatedCount by remember { mutableIntStateOf(0) }
    var showCelebration by remember { mutableStateOf(false) }
    var motivationalMessage by remember { mutableStateOf("") }
    var showAccessibilityAlert by remember { mutableStateOf(!isAccessibilityServiceEnabled) }

    val boxes = remember {
        mutableStateListOf(
            PracticeBox(
                id = 0,
                icon = Icons.Default.Favorite,
                label = Resources.getString(R.string.onboarding_practice_box_1),
                color = Color(0xFFE91E63)
            ),
            PracticeBox(
                id = 1,
                icon = Icons.Default.Star,
                label = Resources.getString(R.string.onboarding_practice_box_2),
                color = Color(0xFFFFC107)
            ),
            PracticeBox(
                id = 2,
                icon = Icons.Default.Celebration,
                label = Resources.getString(R.string.onboarding_practice_box_3),
                color = Color(0xFF4CAF50)
            ),
            PracticeBox(
                id = 3,
                icon = Icons.Default.EmojiEmotions,
                label = Resources.getString(R.string.onboarding_practice_box_4),
                color = Color(0xFF2196F3)
            )
        )
    }

    val motivationalMessages = listOf(
        stringResource(R.string.onboarding_practice_message_1),
        stringResource(R.string.onboarding_practice_message_2),
        stringResource(R.string.onboarding_practice_message_3),
        stringResource(R.string.onboarding_practice_message_4)
    )

    LaunchedEffect(activatedCount) {
        if (activatedCount > 0 && activatedCount < 4) {
            motivationalMessage = motivationalMessages[activatedCount - 1]
        } else if (activatedCount == 4) {
            showCelebration = true
            delay(2000)
            onComplete()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.onboarding_practice_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = stringResource(
                    if (isAccessibilityServiceEnabled) 
                        R.string.onboarding_practice_switch_instructions 
                    else 
                        R.string.onboarding_practice_tap_instructions
                ),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Progress indicator
            LinearProgressIndicator(
                progress = { activatedCount / 4f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Practice boxes grid
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PracticeBoxItem(
                        box = boxes[0],
                        modifier = Modifier.weight(1f),
                        onActivate = {
                            if (!boxes[0].isActivated) {
                                boxes[0] = boxes[0].copy(isActivated = true)
                                activatedCount++
                            }
                        }
                    )
                    PracticeBoxItem(
                        box = boxes[1],
                        modifier = Modifier.weight(1f),
                        onActivate = {
                            if (!boxes[1].isActivated) {
                                boxes[1] = boxes[1].copy(isActivated = true)
                                activatedCount++
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PracticeBoxItem(
                        box = boxes[2],
                        modifier = Modifier.weight(1f),
                        onActivate = {
                            if (!boxes[2].isActivated) {
                                boxes[2] = boxes[2].copy(isActivated = true)
                                activatedCount++
                            }
                        }
                    )
                    PracticeBoxItem(
                        box = boxes[3],
                        modifier = Modifier.weight(1f),
                        onActivate = {
                            if (!boxes[3].isActivated) {
                                boxes[3] = boxes[3].copy(isActivated = true)
                                activatedCount++
                            }
                        }
                    )
                }
            }

            // Motivational message
            AnimatedVisibility(
                visible = motivationalMessage.isNotEmpty(),
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = motivationalMessage,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Celebration overlay
        AnimatedVisibility(
            visible = showCelebration,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut()
        ) {
            CelebrationOverlay()
        }
        
        // Accessibility service alert
        if (showAccessibilityAlert) {
            AccessibilityServiceAlert(
                onEnableService = {
                    serviceUtils.openAccessibilitySettings(context)
                    showAccessibilityAlert = false
                },
                onSkipPractice = {
                    onComplete()
                },
                onDismiss = {
                    showAccessibilityAlert = false
                }
            )
        }
    }
}

@Composable
private fun PracticeBoxItem(
    box: PracticeBox,
    modifier: Modifier = Modifier,
    onActivate: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (box.isActivated) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    val rotation by animateFloatAsState(
        targetValue = if (box.isActivated) 360f else 0f,
        animationSpec = tween(500),
        label = "rotation"
    )

    Card(
        modifier = modifier
            .wrapContentHeight()
            .scale(scale)
            .clickable(enabled = !box.isActivated) { onActivate() },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (box.isActivated) 0.dp else 8.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (box.isActivated)
                box.color.copy(alpha = 0.2f)
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (!box.isActivated) {
            CardDefaults.outlinedCardBorder()
        } else {
            null
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .background(
                    if (box.isActivated)
                        box.color.copy(alpha = 0.1f)
                    else
                        Color.Transparent
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = box.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            rotationZ = rotation
                        },
                    tint = if (box.isActivated) box.color else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = box.label,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    fontWeight = if (box.isActivated) FontWeight.Bold else FontWeight.Normal,
                    color = if (box.isActivated) box.color else MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Checkmark overlay
                AnimatedVisibility(
                    visible = box.isActivated,
                    enter = scaleIn() + fadeIn()
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(top = 4.dp),
                        tint = box.color
                    )
                }
            }
        }
    }
}

@Composable
private fun CelebrationOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated celebration icon
            val infiniteTransition = rememberInfiniteTransition(label = "celebration")
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.8f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )

            Icon(
                imageVector = Icons.Default.Celebration,
                contentDescription = null,
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale),
                tint = Color(0xFFFFC107)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.onboarding_practice_complete),
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = stringResource(R.string.onboarding_practice_ready),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Confetti effect (simplified)
        repeat(20) {
            val offsetX = remember { Random.nextInt(-200, 200).dp }
            val offsetY = remember { Random.nextInt(-300, -100).dp }
            val color = remember {
                listOf(
                    Color(0xFFE91E63),
                    Color(0xFFFFC107),
                    Color(0xFF4CAF50),
                    Color(0xFF2196F3)
                ).random()
            }

            Box(
                modifier = Modifier
                    .offset(x = offsetX, y = offsetY)
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
        }
    }
}

@Composable
private fun AccessibilityServiceAlert(
    onEnableService: () -> Unit,
    onSkipPractice: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.onboarding_practice_accessibility_required_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = stringResource(R.string.onboarding_practice_accessibility_required_message),
                style = MaterialTheme.typography.bodyLarge
            )
        },
        confirmButton = {
            Button(
                onClick = onEnableService
            ) {
                Text(stringResource(R.string.onboarding_practice_enable_service))
            }
        },
        dismissButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSkipPractice
                ) {
                    Text(stringResource(R.string.onboarding_skip_practice))
                }
                
                TextButton(
                    onClick = onDismiss
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        }
    )
}
