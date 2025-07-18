package com.ner.wimap.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A container that provides swipe gesture navigation between screens
 * Main screen (center) -> Swipe right to Pinned, Swipe left to Maps
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeNavigationContainer(
    modifier: Modifier = Modifier,
    initialPage: Int = 1, // Start with Main screen (center)
    pagerState: PagerState? = null, // Optional external pager state
    onPageChanged: (Int) -> Unit = {},
    content: @Composable (pageIndex: Int, pagerState: PagerState) -> Unit
) {
    val internalPagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { 5 } // WiFi Locator (0), Pinned (1), Main (2), Maps (3), Scan History (4)
    )
    val actualPagerState = pagerState ?: internalPagerState
    
    val coroutineScope = rememberCoroutineScope()
    
    // Handle page changes
    LaunchedEffect(actualPagerState.currentPage) {
        onPageChanged(actualPagerState.currentPage)
    }
    
    HorizontalPager(
        state = actualPagerState,
        modifier = modifier.fillMaxSize(),
        pageSpacing = 0.dp
    ) { pageIndex ->
        content(pageIndex, actualPagerState)
    }
}

/**
 * Navigation destinations for the swipe container
 */
enum class SwipeDestination(val index: Int, val title: String, val icon: String) {
    WIFI_LOCATOR(0, "Locator", "🧭"),
    PINNED(1, "Pinned", "📌"),
    MAIN(2, "Main", "📡"),
    MAPS(3, "Map", "🗺"),
    SCAN_HISTORY(4, "Scans", "🧾")
}

/**
 * Extension function to animate to a specific page
 */
@OptIn(ExperimentalFoundationApi::class)
suspend fun PagerState.animateToPage(destination: SwipeDestination) {
    animateScrollToPage(destination.index)
}

/**
 * A swipe indicator that shows current page and swipe directions
 */
@Composable
fun SwipeIndicator(
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SwipeDestination.values().forEachIndexed { index, destination ->
            SwipeIndicatorDot(
                isSelected = currentPage == index,
                label = destination.title
            )
            if (index < SwipeDestination.values().size - 1) {
                Spacer(modifier = Modifier.width(12.dp))
            }
        }
    }
}

@Composable
private fun SwipeIndicatorDot(
    isSelected: Boolean,
    label: String
) {
    val animatedSize by animateDpAsState(
        targetValue = if (isSelected) 10.dp else 8.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "dot_size"
    )
    
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.4f,
        animationSpec = tween(300),
        label = "dot_alpha"
    )
    
    Box(
        modifier = Modifier
            .size(animatedSize)
            .graphicsLayer { alpha = animatedAlpha },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = androidx.compose.foundation.shape.CircleShape,
            color = MaterialTheme.colorScheme.primary
        ) {}
    }
}

/**
 * Enhanced swipe indicator with icons and labels
 */
@Composable
fun SwipeIndicatorWithLabels(
    currentPage: Int,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Dot indicators
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SwipeDestination.values().forEachIndexed { index, destination ->
                SwipeIndicatorDot(
                    isSelected = currentPage == index,
                    label = destination.title
                )
                if (index < SwipeDestination.values().size - 1) {
                    Spacer(modifier = Modifier.width(12.dp))
                }
            }
        }
        
        if (showLabels) {
            Spacer(modifier = Modifier.height(4.dp))
            // Labels with icons
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                SwipeDestination.values().forEachIndexed { index, destination ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = destination.icon,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (currentPage == index) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            text = destination.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (currentPage == index) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}