package com.enaboapps.switchify.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * This is a component used in all screens of the app.
 * It displays a Scaffold with a top bar and manages scrolling behavior.
 *
 * @param titleResId The resource ID of the screen title.
 * @param navController The NavController used to navigate between screens.
 * @param navBarActions The actions to display in the top bar.
 * @param floatingActionButton The floating action button to display in the screen.
 * @param enableScroll Whether to enable scrolling for the content. Defaults to true.
 * @param padding The padding to apply around the content. Defaults to 16.dp.
 * @param showBackButton Whether to show the back button. If null, auto-detect based on nav stack.
 * @param onBackPressed Custom back button action. If null, uses default nav controller pop.
 * @param headerContent Optional content to display above the main content.
 * @param content The content of the screen.
 */
@Composable
fun BaseView(
    titleResId: Int,
    navController: androidx.navigation.NavController,
    navBarActions: List<NavBarAction> = emptyList(),
    floatingActionButton: @Composable () -> Unit = {},
    enableScroll: Boolean = true,
    padding: Dp = 16.dp,
    showBackButton: Boolean? = null,
    onBackPressed: (() -> Unit)? = null,
    headerContent: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val title = stringResource(titleResId)
    Scaffold(
        topBar = {
            NavBar(title, navController, navBarActions, showBackButton, onBackPressed)
        },
        floatingActionButton = floatingActionButton
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header content (if provided)
            headerContent?.invoke()
            
            // Main content
            if (enableScroll) {
                ScrollableView(modifier = Modifier.weight(1f)) {
                    content()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    content()
                }
            }
        }
    }
}