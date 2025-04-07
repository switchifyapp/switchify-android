package com.enaboapps.switchify.screens.settings.patterns

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.service.gestures.patterns.model.GesturePattern

@Composable
fun GesturePatternsScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: GesturePatternsViewModel = viewModel()

    // Initialize the ViewModel
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    // Collect state from ViewModel
    val patterns by viewModel.patterns.collectAsState()
    val isEditDialogVisible by viewModel.isEditDialogVisible.collectAsState()
    val selectedPattern by viewModel.selectedPattern.collectAsState()
    val newPatternName by viewModel.newPatternName.collectAsState()

    // Edit Dialog
    if (isEditDialogVisible && selectedPattern != null) {
        EditPatternNameDialog(
            currentName = newPatternName,
            onNameChange = { viewModel.updatePatternName(it) },
            onDismiss = { viewModel.hideEditDialog() },
            onSave = { viewModel.savePatternName() }
        )
    }

    BaseView(
        titleResId = R.string.screen_title_gesture_patterns,
        navController = navController
    ) {
        Text(
            text = stringResource(R.string.gesture_patterns_description),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp)
        )

        Section(titleResId = R.string.gesture_patterns_title) {
            if (patterns.isEmpty()) {
                Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.gesture_patterns_empty),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            } else {
                patterns.forEach { pattern ->
                    PatternItem(
                        pattern = pattern,
                        onEdit = { viewModel.showEditDialog(pattern) },
                        onDelete = {
                            viewModel.deletePattern(pattern)
                            Toast.makeText(
                                context,
                                context.getString(R.string.pattern_deleted_message),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PatternItem(
    pattern: GesturePattern,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = pattern.name,
                style = MaterialTheme.typography.titleMedium
            )

            Row {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit_pattern)
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete_pattern)
                    )
                }
            }
        }
    }
}

@Composable
private fun EditPatternNameDialog(
    currentName: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.edit_pattern_name),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = currentName,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.pattern_name)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(onClick = onSave) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}