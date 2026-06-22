package com.enaboapps.switchify.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
fun <T> EqualHeightGridRow(
    items: List<T?>,
    columns: Int,
    itemWidth: Dp,
    minItemHeight: Dp,
    horizontalGap: Dp,
    modifier: Modifier = Modifier,
    emptyItem: @Composable (Modifier) -> Unit = { itemModifier ->
        Spacer(
            modifier = itemModifier
                .fillMaxHeight()
                .heightIn(min = minItemHeight)
        )
    },
    itemContent: @Composable (item: T, modifier: Modifier) -> Unit
) {
    require(columns > 0)
    require(items.size <= columns)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(horizontalGap)
    ) {
        items.forEach { item ->
            val itemModifier = Modifier
                .width(itemWidth)
                .fillMaxHeight()

            if (item == null) {
                emptyItem(itemModifier)
            } else {
                itemContent(item, itemModifier)
            }
        }

        repeat(columns - items.size) {
            val itemModifier = Modifier
                .width(itemWidth)
                .fillMaxHeight()

            emptyItem(itemModifier)
        }
    }
}
