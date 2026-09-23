package com.enaboapps.switchify.service.menu

internal object MenuItemOrdering {
    fun <T> withLeadingItems(leadingItems: List<T>, orderedItems: List<T>): List<T> =
        leadingItems + orderedItems
}
