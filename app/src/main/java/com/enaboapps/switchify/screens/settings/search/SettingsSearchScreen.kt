package com.enaboapps.switchify.screens.settings.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.Panel
import com.enaboapps.switchify.components.SwitchifyTextField
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.nav.navigateIfResumed
import com.enaboapps.switchify.theme.Dimens

@Composable
fun SettingsSearchScreen(navController: NavController) {
    val context = LocalContext.current
    val items = remember {
        GeneratedSettingsSearchIndex.entries.map { entry ->
            SettingsSearchItem(
                entry = entry,
                title = context.getString(entry.titleResId),
                summary = entry.summaryResId?.let(context::getString),
                explanation = entry.explanationResId?.let(context::getString),
                path = listOfNotNull(entry.screenTitleResId, entry.sectionTitleResId).map(context::getString)
            )
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { SettingsSearchRanker.rank(query, items) }

    BaseView(
        titleResId = R.string.settings_search_title,
        navController = navController,
        enableScroll = false
    ) {
        SwitchifyTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.settings_search_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
            singleLine = true
        )
        when {
            query.isBlank() -> SearchMessage(stringResource(R.string.settings_search_hint))
            results.isEmpty() -> SearchMessage(stringResource(R.string.settings_search_no_results, query.trim()))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = Dimens.spaceM),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)
            ) {
                items(results, key = { "${it.entry.route}/${it.entry.tab}/${it.entry.titleResId}" }) { item ->
                    SearchResultRow(item) { openResult(navController, item.entry) }
                }
            }
        }
    }
}

@Composable
private fun SearchMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(Dimens.spaceM)
    )
}

@Composable
private fun SearchResultRow(item: SettingsSearchItem, onClick: () -> Unit) {
    Panel(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(Dimens.spaceM)) {
            Text(text = item.title, style = MaterialTheme.typography.titleMedium)
            if (!item.summary.isNullOrBlank()) {
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (item.path.isNotEmpty()) {
                Text(
                    text = item.path.joinToString(" › "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Dimens.spaceXs)
                )
            }
        }
    }
}

private fun openResult(navController: NavController, entry: SettingsSearchEntry) {
    if (navController.currentDestination?.route != NavigationRoute.SettingsSearch.name) return
    SettingsSearchHighlight.request(entry)
    if (entry.route == NavigationRoute.Settings.name) {
        navController.popBackStack()
    } else {
        navController.navigateIfResumed(entry.route) {
            popUpTo(NavigationRoute.SettingsSearch.name) { inclusive = true }
        }
    }
}
