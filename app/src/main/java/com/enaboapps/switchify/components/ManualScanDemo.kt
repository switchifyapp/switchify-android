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
import com.enaboapps.switchify.components.FullWidthButton
import kotlinx.coroutines.delay

@Composable
fun ManualScanDemo(
    color: Color,
    modifier: Modifier = Modifier,
    itemCount: Int = 4
) {
    var selectedItem by remember { mutableIntStateOf(-1) }
    var highlightedItem by remember { mutableIntStateOf(0) }
    
    val moveNext = {
        highlightedItem = (highlightedItem + 1) % itemCount
    }
    
    val movePrevious = {
        highlightedItem = if (highlightedItem == 0) itemCount - 1 else highlightedItem - 1
    }
    
    val selectItem = {
        selectedItem = highlightedItem
    }
    
    LaunchedEffect(selectedItem) {
        if (selectedItem != -1) {
            delay(1500)
            selectedItem = -1
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
                    isHighlighted = highlightedItem == index,
                    isSelected = selectedItem == index,
                    color = color,
                    label = "Item ${index + 1}"
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Manual scan controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FullWidthButton(
                textResId = R.string.action_previous,
                onClick = movePrevious,
                modifier = Modifier.weight(1f)
            )
            FullWidthButton(
                textResId = R.string.action_next,
                onClick = moveNext,
                modifier = Modifier.weight(1f)
            )
            FullWidthButton(
                textResId = R.string.action_select,
                onClick = selectItem,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ScanDemoItem(
    isHighlighted: Boolean,
    isSelected: Boolean,
    color: Color,
    label: String
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.2f else if (isHighlighted) 1.1f else 1f,
        animationSpec = spring(),
        label = "scale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isSelected || isHighlighted) 1f else 0.6f,
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
                    color = when {
                        isSelected -> color.copy(alpha = 0.4f)
                        isHighlighted -> color.copy(alpha = 0.2f)
                        else -> Color.Transparent
                    },
                    shape = RoundedCornerShape(8.dp)
                )
                .border(
                    width = when {
                        isSelected -> 4.dp
                        isHighlighted -> 3.dp
                        else -> 1.dp
                    },
                    color = when {
                        isSelected -> color
                        isHighlighted -> color
                        else -> Color.Gray
                    },
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Square,
                contentDescription = null,
                tint = when {
                    isSelected -> color
                    isHighlighted -> color
                    else -> Color.Gray
                },
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