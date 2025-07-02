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
        pageCount = { 3 } // Pinned (0), Main (1), Maps (2)
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
enum class SwipeDestination(val index: Int, val title: String) {
    PINNED(0, "Pinned Networks"),
    MAIN(1, "WiFi Scanner"),
    MAPS(2, "Network Map")
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
        targetValue = if (isSelected) 8.dp else 6.dp,
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