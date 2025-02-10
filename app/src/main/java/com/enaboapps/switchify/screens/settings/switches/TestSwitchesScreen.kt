package com.enaboapps.switchify.screens.settings.switches

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.enaboapps.switchify.components.NavBar

@Composable
fun TestSwitchesScreen(navController: NavHostController) {
    val scrollState = rememberScrollState()
    var switchState by remember { mutableStateOf("You haven't pressed any switch yet") }

    val context = LocalContext.current

    Scaffold(
        topBar = {
            NavBar(title = "Test Switches", navController = navController)
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            SwitchListener(
                onKeyEvent = { keyEvent ->
                    switchState = when (keyEvent.nativeKeyEvent.action) {
                        android.view.KeyEvent.ACTION_DOWN -> {
                            Log.d(
                                "TestSwitchesScreen",
                                "Switch pressed, code: ${keyEvent.nativeKeyEvent.keyCode}"
                            )
                            Toast.makeText(
                                context,
                                "Switch pressed, code: ${keyEvent.nativeKeyEvent.keyCode}",
                                Toast.LENGTH_SHORT
                            ).show()
                            "You are pressing the switch"
                        }

                        android.view.KeyEvent.ACTION_UP -> {
                            Log.d(
                                "TestSwitchesScreen",
                                "Switch released, code: ${keyEvent.nativeKeyEvent.keyCode}"
                            )
                            Toast.makeText(
                                context,
                                "Switch released, code: ${keyEvent.nativeKeyEvent.keyCode}",
                                Toast.LENGTH_SHORT
                            ).show()
                            "You released the switch"
                        }

                        else -> switchState
                    }
                    true
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            SwitchIndicator(switchState = switchState)

            Spacer(modifier = Modifier.height(32.dp))

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Instructions:\n" +
                        "1. Ensure your switch is connected.\n" +
                        "2. Press your switch to see it detected here.\n" +
                        "3. The text above will change based on the state of your switch.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun SwitchListener(onKeyEvent: (KeyEvent) -> Boolean) {
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .focusRequester(focusRequester)
            .onKeyEvent(onKeyEvent)
            .focusable()
    ) {
        Text(
            text = "Use this area to test your switch before assigning it to an action. To begin, press your switch.",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Center)
        )
    }

    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
    }
}

@Composable
fun SwitchIndicator(switchState: String) {
    Box(
        modifier = Modifier
            .padding(8.dp)
    ) {
        Text(
            text = switchState,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}