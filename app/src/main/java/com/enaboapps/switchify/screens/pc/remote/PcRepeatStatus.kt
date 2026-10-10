package com.enaboapps.switchify.screens.pc.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.ActionButtonType
import com.enaboapps.switchify.components.Panel
import com.enaboapps.switchify.pc.protocol.PcCommandTypes
import com.enaboapps.switchify.pc.remote.PcRemotePresentation
import com.enaboapps.switchify.pc.remote.PcRemoteSession
import com.enaboapps.switchify.pc.remote.PcRemoteSessionState
import com.enaboapps.switchify.theme.Dimens

@Composable
fun PcRepeatStatus(
    session: PcRemoteSession,
    state: PcRemoteSessionState,
    physicalSwitchStopAvailable: Boolean,
    onStop: () -> Unit
) {
    val repeat = state.repeat
    if (repeat != null) {
        val stopLabel = PcRemotePresentation.repeatStopLabel(repeat)
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
            ActionButton(
                textResId = stopLabel,
                onClick = onStop,
                type = ActionButtonType.DESTRUCTIVE,
                leadingIcon = Icons.Rounded.StopCircle,
                applyPadding = false,
                modifier = Modifier.fillMaxWidth()
            )
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            ) {
                Row(
                    modifier = Modifier.padding(Dimens.spaceS),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Rounded.Autorenew, contentDescription = null)
                    Text(
                        text = stringResource(
                            if (state.repeatingKey) R.string.pc_repeat_key_status else R.string.pc_repeat_movement_status,
                            stringResource(stopLabel)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
    if (!physicalSwitchStopAvailable && repeatCouldStart(session)) {
        Panel(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.pc_repeat_switch_stop_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Dimens.spaceM)
            )
        }
    }
}

private fun repeatCouldStart(session: PcRemoteSession): Boolean {
    val capabilities = session.profile?.capabilities ?: return false
    if (!session.supports(PcCommandTypes.MOUSE_REPEAT_START) || !session.supports(PcCommandTypes.MOUSE_REPEAT_STOP)) return false
    return (capabilities.mouseRepeat.supported && capabilities.mouseRepeat.enabled) ||
        (capabilities.keyRepeat.supported && capabilities.keyRepeat.enabled)
}
