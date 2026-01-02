package com.enaboapps.switchify.screens.stats.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.service.stats.models.DailyActivity
import kotlin.math.max

/**
 * Simple bar chart for displaying daily activity.
 */
@Composable
fun ActivityChart(
    data: List<DailyActivity>,
    title: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (data.isEmpty()) {
                Text(
                    text = "No data available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                SimpleBarChart(
                    data = data,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(top = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun SimpleBarChart(
    data: List<DailyActivity>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Canvas(modifier = modifier) {
        if (data.isEmpty()) return@Canvas

        val maxValue = max(
            data.maxOfOrNull { it.switchPresses + it.menuOpens } ?: 1,
            1
        ).toFloat()

        val barWidth = size.width / (data.size * 2)
        val spacing = barWidth / 2

        data.forEachIndexed { index, activity ->
            val totalActivity = activity.switchPresses + activity.menuOpens
            val barHeight = (totalActivity / maxValue) * size.height

            // Draw bar
            val x = index * (barWidth * 2) + spacing
            val y = size.height - barHeight

            drawRect(
                color = primaryColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight)
            )
        }
    }
}
