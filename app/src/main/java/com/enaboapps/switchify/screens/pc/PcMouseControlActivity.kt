package com.enaboapps.switchify.screens.pc

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.activities.ui.theme.SwitchifyTheme
import com.enaboapps.switchify.components.NavBar
import com.enaboapps.switchify.pc.PcCommandResult
import com.enaboapps.switchify.pc.PcConnectionState
import com.enaboapps.switchify.pc.PcConnectionStateHolder
import com.enaboapps.switchify.pc.PcDeviceIdentityRepository
import com.enaboapps.switchify.pc.PcMouseCommand
import com.enaboapps.switchify.pc.PcTokenStore
import com.enaboapps.switchify.pc.SwitchifyPcClient
import kotlinx.coroutines.launch

class PcMouseControlActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SwitchifyTheme {
                PcMouseControlScreen(
                    context = applicationContext,
                    onClose = { finish() }
                )
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, PcMouseControlActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }
}

@Composable
private fun PcMouseControlScreen(context: Context, onClose: () -> Unit) {
    val tokenStore = remember { PcTokenStore(context) }
    val client = remember { SwitchifyPcClient(PcDeviceIdentityRepository(context), tokenStore) }
    val connectionState by PcConnectionStateHolder.connectionState.collectAsState()
    val connected = connectionState as? PcConnectionState.Connected
    val scope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    DisposableEffect(client) {
        onDispose { client.close() }
    }

    Scaffold(
        topBar = {
            NavBar(
                title = stringResource(R.string.menu_title_control_pc),
                showBackButton = true,
                onBackPressed = onClose
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = connected?.let { stringResource(R.string.pc_mouse_control_connected, it.displayName) }
                        ?: stringResource(R.string.pc_control_connect_first),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                statusMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(pcMouseControls()) { control ->
                        Button(
                            onClick = {
                                val session = connected?.session
                                if (session == null) {
                                    statusMessage = context.getString(R.string.pc_control_connect_first)
                                    return@Button
                                }
                                isBusy = true
                                scope.launch {
                                    when (client.sendMouseCommand(session, control.command)) {
                                        PcCommandResult.Ack -> statusMessage = null
                                        is PcCommandResult.AuthFailed -> {
                                            tokenStore.clearToken(session.desktopId)
                                            PcConnectionStateHolder.setDisconnected()
                                            statusMessage = context.getString(R.string.pc_control_connect_first)
                                        }
                                        is PcCommandResult.Failed -> {
                                            statusMessage = context.getString(R.string.pc_control_command_failed)
                                        }
                                    }
                                    isBusy = false
                                }
                            },
                            enabled = connected != null && !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 84.dp)
                        ) {
                            Text(
                                text = stringResource(control.labelResId),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class PcMouseControl(
    val labelResId: Int,
    val command: PcMouseCommand
)

private fun pcMouseControls(): List<PcMouseControl> {
    val step = 80
    val scrollStep = 5
    return listOf(
        PcMouseControl(R.string.pc_mouse_up_left, PcMouseCommand.Move(-step, -step)),
        PcMouseControl(R.string.pc_mouse_up, PcMouseCommand.Move(0, -step)),
        PcMouseControl(R.string.pc_mouse_up_right, PcMouseCommand.Move(step, -step)),
        PcMouseControl(R.string.pc_mouse_left, PcMouseCommand.Move(-step, 0)),
        PcMouseControl(R.string.pc_mouse_click, PcMouseCommand.LeftClick),
        PcMouseControl(R.string.pc_mouse_right, PcMouseCommand.Move(step, 0)),
        PcMouseControl(R.string.pc_mouse_down_left, PcMouseCommand.Move(-step, step)),
        PcMouseControl(R.string.pc_mouse_down, PcMouseCommand.Move(0, step)),
        PcMouseControl(R.string.pc_mouse_down_right, PcMouseCommand.Move(step, step)),
        PcMouseControl(R.string.pc_mouse_right_click, PcMouseCommand.RightClick),
        PcMouseControl(R.string.pc_mouse_double_click, PcMouseCommand.DoubleClick),
        PcMouseControl(R.string.pc_mouse_scroll_up, PcMouseCommand.Scroll(0, scrollStep)),
        PcMouseControl(R.string.pc_mouse_scroll_down, PcMouseCommand.Scroll(0, -scrollStep))
    )
}
