package com.enaboapps.switchify.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Square
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import kotlinx.coroutines.delay

@Composable
fun AutoScanDemo(
    color: Color,
    modifier: Modifier = Modifier,
    itemCount: Int = 2,
    scanDelay: Long = 1000L
) {
    var currentItem by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(Unit) {
        while (true) {
            delay(scanDelay)
            currentItem = (currentItem + 1) % itemCount
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        // Demo label
        Text(
            text = stringResource(R.string.demo),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            repeat(itemCount) { index ->
                ScanDemoItem(
                    isHighlighted = currentItem == index,
                    color = color,
                    label = "Item ${index + 1}"
                )
            }
        }
    }
}

@Composable
private fun ScanDemoItem(
    isHighlighted: Boolean,
    color: Color,
    label: String
) {
    val scale by animateFloatAsState(
        targetValue = if (isHighlighted) 1.1f else 1f,
        animationSpec = spring(),
        label = "scale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isHighlighted) 1f else 0.6f,
        label = "alpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .alpha(alpha)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(
                    color = if (isHighlighted) color.copy(alpha = 0.2f) else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                )
                .border(
                    width = if (isHighlighted) 3.dp else 1.dp,
                    color = if (isHighlighted) color else Color.Gray,
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Square,
                contentDescription = null,
                tint = if (isHighlighted) color else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}