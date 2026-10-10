package com.enaboapps.switchify.screens.pc.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.remote.actions.PcResolvedAction
import com.enaboapps.switchify.pc.remote.layouts.PcButtonLayout
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutEditorState
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutSections
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutSurface
import com.enaboapps.switchify.pc.remote.layouts.PcSectionDefinition
import com.enaboapps.switchify.pc.remote.layouts.PcSurfaceLayouts
import com.enaboapps.switchify.theme.Dimens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class PcLayoutEditing(
    val enabled: Boolean,
    val blocked: String?,
    val current: () -> PcSurfaceLayouts,
    val load: suspend () -> Unit,
    val save: suspend (PcLayoutSurface, String, PcButtonLayout?) -> Unit
)

val LocalPcLayoutEditing = compositionLocalOf<PcLayoutEditing?> { null }

@Composable
fun PcLayoutEditToggle(enabled: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconToggleButton(
        checked = enabled,
        onCheckedChange = { onToggle() },
        modifier = modifier.size(58.dp)
    ) {
        Icon(
            imageVector = if (enabled) Icons.Rounded.Check else Icons.Rounded.Edit,
            contentDescription = stringResource(if (enabled) R.string.pc_layout_done_editing else R.string.pc_layout_edit_layout)
        )
    }
}

@Composable
fun PcSectionEditing(
    surface: PcLayoutSurface,
    section: String,
    definition: PcSectionDefinition,
    controls: List<PcResolvedAction>
) {
    val editing = LocalPcLayoutEditing.current ?: return
    val current by rememberUpdatedState(editing)
    var editor by remember { mutableStateOf<PcLayoutEditorState?>(null) }
    var opening by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val fontScale = LocalDensity.current.fontScale
    val title = stringResource(definition.titleRes)
    if (editing.enabled) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val width = maxWidth.value
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
                PcControlButton(
                    label = stringResource(R.string.pc_layout_edit_section),
                    accessibilityLabel = stringResource(R.string.pc_layout_edit_section_description, title),
                    icon = Icons.Rounded.Edit,
                    enabled = editing.blocked == null,
                    onClick = {
                        if (!opening && current.enabled && current.blocked == null) {
                            opening = true
                            scope.launch {
                                try {
                                    current.load()
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                } finally {
                                    opening = false
                                }
                                val latest = current
                                if (!latest.enabled || latest.blocked != null) return@launch
                                val defaults = PcLayoutSections.sectionDefault(definition, width, fontScale)
                                val stored = latest.current()[surface]?.get(section)
                                val customized = PcLayoutSections.isValidSectionLayout(surface, section, stored)
                                editor = PcLayoutEditorState(
                                    surface = surface,
                                    initial = if (customized && stored != null) stored else defaults,
                                    defaultLayout = defaults,
                                    initiallyCustomized = customized
                                )
                            }
                        }
                    }
                )
                editing.blocked?.let { blocked ->
                    Text(
                        text = blocked,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    editor?.let { session ->
        PcLayoutEditorDialog(
            title = title,
            initialState = session,
            controls = controls,
            onSave = { layout -> current.save(surface, section, layout) },
            onClose = { editor = null }
        )
    }
}
