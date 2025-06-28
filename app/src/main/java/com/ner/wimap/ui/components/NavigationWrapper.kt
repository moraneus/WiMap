package com.ner.wimap.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/**
 * Navigation wrapper that provides consistent top bar with navigation indicators
 */
@Composable
fun NavigationWrapper(
    currentPage: Int,
    onNavigateToPage: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onShowAbout: () -> Unit,
    onShowTerms: () -> Unit,
    isBackgroundServiceActive: Boolean = false,
    content: @Composable (PaddingValues) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        MainTopAppBar(
            onOpenPinnedNetworks = { onNavigateToPage(0) },
            onOpenSettings = onOpenSettings,
            isBackgroundServiceActive = isBackgroundServiceActive,
            showNavigationActions = true,
            onShowAbout = onShowAbout,
            onShowTerms = onShowTerms,
            currentPage = currentPage,
            onNavigateToPage = onNavigateToPage
        )
        
        content(PaddingValues())
    }
}