package com.enaboapps.switchify.screens.pc.remote

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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
        PcSectionEditing(surface, section, definition, controls)
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
    val keySize = PcLayoutSections.keySize(LocalDensity.current.fontScale)
    val gap = group.gap ?: PcLayoutSections.DEFAULT_GAP
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = PcLayoutSections.keyColumns(group, maxWidth.value, keySize, gap)
        val rows = group.ids.mapNotNull { controls[it] }.chunked(columns)
        KeyGrid(rows, columns, keySize, gap, maxWidth.value, onAction)
    }
}

@Composable
private fun CustomGrid(
    layout: PcButtonLayout,
    controls: Map<String, PcResolvedAction>,
    onAction: (PcResolvedAction) -> Unit
) {
    val keySize = PcLayoutSections.keySize(LocalDensity.current.fontScale)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val rows = List(layout.rows) { rowIndex -> layout.row(rowIndex).map { id -> id?.let(controls::get) } }
        KeyGrid(rows, layout.columns, keySize, PcLayoutSections.DEFAULT_GAP, maxWidth.value, onAction)
    }
}

@Composable
private fun KeyGrid(
    rows: List<List<PcResolvedAction?>>,
    columns: Int,
    keySize: Int,
    gap: Int,
    viewportWidth: Float,
    onAction: (PcResolvedAction) -> Unit
) {
    val gridWidth = PcLayoutSections.keyGridWidth(columns, keySize, gap)
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .then(if (gridWidth > viewportWidth) Modifier.horizontalScroll(scroll) else Modifier)
            .width(gridWidth.dp),
        verticalArrangement = Arrangement.spacedBy(gap.dp)
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                row.forEach { action ->
                    if (action != null) {
                        PcActionControl(
                            action = action,
                            onClick = { onAction(action) },
                            modifier = Modifier.size(keySize.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.size(keySize.dp))
                    }
                }
            }
        }
    }
}

private val GAP = Dimens.spaceXs
