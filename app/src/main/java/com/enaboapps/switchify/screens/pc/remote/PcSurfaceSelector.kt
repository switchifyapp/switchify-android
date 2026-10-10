package com.enaboapps.switchify.screens.pc.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.pc.remote.PcRemoteSurface
import com.enaboapps.switchify.screens.pc.pcSurfaceLabel

private val TrackShape = RoundedCornerShape(24.dp)
private val SegmentShape = RoundedCornerShape(20.dp)
private val TrackPadding = 4.dp
private val LabelPadding = 6.dp
private val MinSegmentHeight = 48.dp

object PcSurfaceSelectorLayout {
    fun columns(itemCount: Int, availableWidth: Float, widestLabel: Float, labelPadding: Float): Int {
        if (itemCount <= 1) return itemCount
        val fitsOneRow = availableWidth / itemCount >= widestLabel + labelPadding
        return if (fitsOneRow) itemCount else (itemCount + 1) / 2
    }
}

@Composable
fun PcSurfaceSelector(selected: PcRemoteSurface, onSelect: (PcRemoteSurface) -> Unit) {
    val surfaces = PcRemoteSurface.entries
    val labels = surfaces.map { stringResource(pcSurfaceLabel(it)) }
    val style = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = TrackShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(TrackPadding)) {
            val widest = labels.maxOf { measurer.measure(it, style).size.width }.toFloat()
            val columns = with(density) {
                PcSurfaceSelectorLayout.columns(
                    itemCount = surfaces.size,
                    availableWidth = maxWidth.toPx(),
                    widestLabel = widest,
                    labelPadding = (LabelPadding * 2).toPx()
                )
            }
            Column(modifier = Modifier.selectableGroup()) {
                surfaces.indices.chunked(columns).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        row.forEach { index ->
                            val isSelected = surfaces[index] == selected
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .heightIn(min = MinSegmentHeight)
                                    .clip(SegmentShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                    .selectable(
                                        selected = isSelected,
                                        role = Role.Tab,
                                        onClick = { onSelect(surfaces[index]) }
                                    )
                                    .padding(horizontal = LabelPadding, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = labels[index],
                                    style = style,
                                    textAlign = TextAlign.Center,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        repeat(columns - row.size) { Box(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
