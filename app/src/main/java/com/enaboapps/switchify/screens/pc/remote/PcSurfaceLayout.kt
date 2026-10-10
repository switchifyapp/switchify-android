package com.enaboapps.switchify.screens.pc.remote

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.pc.remote.actions.PcResolvedAction
import com.enaboapps.switchify.pc.remote.layouts.PcButtonLayout
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutSections
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutSurface
import com.enaboapps.switchify.pc.remote.layouts.PcSectionGroup
import com.enaboapps.switchify.pc.remote.layouts.PcSurfaceLayouts
import com.enaboapps.switchify.theme.Dimens

@Composable
fun PcSurfaceLayout(
    surface: PcLayoutSurface,
    section: String,
    controls: List<PcResolvedAction>,
    layouts: PcSurfaceLayouts,
    onAction: (PcResolvedAction) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    headerAccessory: (@Composable () -> Unit)? = null
) {
    val definition = PcLayoutSections.get(surface, section) ?: return
    val stored = layouts[surface]?.get(section)
    val layout = stored?.takeIf { PcLayoutSections.isValidSectionLayout(surface, section, it) }
    val byId = controls.associateBy { it.id }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(GAP)
    ) {
        Text(
            text = title ?: stringResource(definition.titleRes),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        headerAccessory?.invoke()
        if (layout != null) {
            CustomGrid(layout, byId, onAction)
        } else {
            definition.groups.forEach { group -> DefaultGroup(group, byId, onAction) }
        }
    }
}

@Composable
private fun DefaultGroup(
    group: PcSectionGroup,
    controls: Map<String, PcResolvedAction>,
    onAction: (PcResolvedAction) -> Unit
) {
    val fontScale = LocalDensity.current.fontScale
    val gap = (group.gap ?: PcLayoutSections.DEFAULT_GAP).dp
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = PcLayoutSections.groupColumns(group, maxWidth.value, fontScale)
        val items = group.ids.mapNotNull { controls[it] }
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            items.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    row.forEach { action ->
                        PcActionControl(
                            action = action,
                            onClick = { onAction(action) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CustomGrid(
    layout: PcButtonLayout,
    controls: Map<String, PcResolvedAction>,
    onAction: (PcResolvedAction) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val metrics = PcLayoutSections.gridMetrics(maxWidth.value, layout.columns)
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .then(if (metrics.overflows) Modifier.horizontalScroll(scroll) else Modifier)
                .width(metrics.gridWidth.dp),
            verticalArrangement = Arrangement.spacedBy(GAP)
        ) {
            repeat(layout.rows) { rowIndex ->
                Row(
                    modifier = Modifier.heightIn(min = 58.dp),
                    horizontalArrangement = Arrangement.spacedBy(GAP)
                ) {
                    layout.row(rowIndex).forEach { id ->
                        val action = id?.let(controls::get)
                        if (action != null) {
                            PcActionControl(
                                action = action,
                                onClick = { onAction(action) },
                                modifier = Modifier.width(metrics.cellWidth.dp),
                                stacked = true
                            )
                        } else {
                            Spacer(modifier = Modifier.width(metrics.cellWidth.dp))
                        }
                    }
                }
            }
        }
    }
}

private val GAP = Dimens.spaceXs
