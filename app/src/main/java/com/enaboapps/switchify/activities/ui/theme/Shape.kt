package com.enaboapps.switchify.activities.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Corner radius values
private val ExtraSmall = 16.dp
private val Small = 24.dp
private val Medium = 32.dp
private val Large = 48.dp
private val ExtraLarge = 64.dp

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(ExtraSmall),
    small = RoundedCornerShape(Small),
    medium = RoundedCornerShape(Medium),
    large = RoundedCornerShape(Large),
    extraLarge = RoundedCornerShape(ExtraLarge)
)