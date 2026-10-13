package com.enaboapps.switchify.screens.settings.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.Picker
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.utils.Resources

@Composable
fun BlockCountSection(
    initialBlockCount: Int,
    onBlockCountChanged: (Int) -> Unit
) {
    var currentBlockCount by remember { mutableIntStateOf(initialBlockCount) }
    val blockCounts = listOf("2", "3", "4", "5", "6", "7", "8", "9", "10")
    Section(titleResId = R.string.section_title_block_settings) {
        Picker(
            titleResId = R.string.preference_title_block_count,
            selectedItem = currentBlockCount.toString(),
            items = blockCounts,
            onItemSelected = { item ->
                currentBlockCount = item.toInt()
                onBlockCountChanged(item.toInt())
            },
            itemToString = { it },
            itemDescription = { value ->
                Resources.getString(
                    R.string.preference_summary_grid_size_dynamic,
                    value
                )
            }
        )
    }
}
