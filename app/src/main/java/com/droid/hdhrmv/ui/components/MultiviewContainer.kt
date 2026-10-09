package com.droid.hdhrmv.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.droid.hdhrmv.model.MultiviewMode
import com.droid.hdhrmv.model.SlotState
import com.droid.hdhrmv.ui.MultiviewLayoutManager
import com.droid.hdhrmv.ui.theme.PrimaryCyan
import com.droid.hdhrmv.ui.theme.TextPrimary
import com.droid.hdhrmv.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
fun MultiviewContainer(
    mode: MultiviewMode,
    slots: List<SlotState>,
    focusedSlotIndex: Int,
    renderVideo: @Composable (Int) -> Unit = {},
    onSlotClick: (Int) -> Unit,
    onSlotLongClick: (Int) -> Unit = {},
    onChannelClick: (Int) -> Unit,
    onMuteToggle: (Int) -> Unit,
    onFocusClick: (Int) -> Unit,
    onSlotSelected: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (mode) {
            MultiviewMode.GRID_4 -> {
                Grid4Layout(
                    slots = slots,
                    focusedIndex = focusedSlotIndex,
                    renderVideo = renderVideo,
                    onSlotClick = onSlotClick,
                    onSlotLongClick = onSlotLongClick,
                    onSlotSelected = onSlotSelected,
                    onChannelClick = onChannelClick,
                    onMuteToggle = onMuteToggle,
                    onFocusClick = onFocusClick
                )
            }
            MultiviewMode.FOCUS_1_PLUS_3 -> {
                Focus1Plus3Layout(
                    slots = slots,
                    focusedIndex = focusedSlotIndex,
                    renderVideo = renderVideo,
                    onSlotClick = onSlotClick,
                    onSlotLongClick = onSlotLongClick,
                    onSlotSelected = onSlotSelected,
                    onChannelClick = onChannelClick,
                    onMuteToggle = onMuteToggle,
                    onFocusClick = onFocusClick
                )
            }
            MultiviewMode.PIP -> {
                PipLayout(
                    slots = slots,
                    focusedIndex = focusedSlotIndex,
                    renderVideo = renderVideo,
                    onSlotClick = onSlotClick,
                    onSlotLongClick = onSlotLongClick,
                    onSlotSelected = onSlotSelected,
                    onChannelClick = onChannelClick,
                    onMuteToggle = onMuteToggle,
                    onFocusClick = onFocusClick
                )
            }
            MultiviewMode.FULLSCREEN -> {
                SingleFullscreenLayout(
                    slot = slots.getOrNull(focusedSlotIndex) ?: slots[0],
                    renderVideo = renderVideo,
                    onSlotClick = onSlotClick,
                    onSlotLongClick = onSlotLongClick,
                    onChannelClick = onChannelClick,
                    onMuteToggle = onMuteToggle,
                    onFocusClick = onFocusClick
                )
            }
            MultiviewMode.CAROUSEL -> {
                CarouselLayout(
                    slots = slots,
                    focusedIndex = focusedSlotIndex,
                    renderVideo = renderVideo,
                    onSlotClick = onSlotClick,
                    onSlotLongClick = onSlotLongClick,
                    onSlotSelected = onSlotSelected,
                    onChannelClick = onChannelClick,
                    onMuteToggle = onMuteToggle,
                    onFocusClick = onFocusClick
                )
            }
        }
    }
}

@Composable
private fun Grid4Layout(
    slots: List<SlotState>,
    focusedIndex: Int,
    renderVideo: @Composable (Int) -> Unit,
    onSlotClick: (Int) -> Unit,
    onSlotLongClick: (Int) -> Unit,
    onSlotSelected: (Int) -> Unit,
    onChannelClick: (Int) -> Unit,
    onMuteToggle: (Int) -> Unit,
    onFocusClick: (Int) -> Unit
) {
    val focusRequesters = remember { List(4) { FocusRequester() } }

    LaunchedEffect(focusedIndex) {
        if (focusedIndex in 0 until 4) {
            try {
                focusRequesters[focusedIndex].requestFocus()
            } catch (_: Exception) {}
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val targetAspect = 16f / 9f
        val arrangement = MultiviewLayoutManager.calculateOptimalGridArrangement(
            availableWidth = maxWidth.value,
            availableHeight = maxHeight.value,
            aspectRatio = targetAspect
        )

        val isStack = (arrangement == MultiviewLayoutManager.GridArrangement.STACK_1X4)

        // Compute overall container bounding box to maintain exact 16:9 cell aspect ratio
        val (containerWidth, containerHeight) = if (isStack) {
            val stackAspect = targetAspect / 4f // 4/9 (~0.444)
            val currentAspect = maxWidth / maxHeight
            if (currentAspect > stackAspect) {
                Pair(maxHeight * stackAspect, maxHeight)
            } else {
                Pair(maxWidth, maxWidth / stackAspect)
            }
        } else {
            val currentAspect = maxWidth / maxHeight
            if (currentAspect > targetAspect) {
                Pair(maxHeight * targetAspect, maxHeight)
            } else {
                Pair(maxWidth, maxWidth / targetAspect)
            }
        }

        // Single persistent Layout: children NEVER change containers or unmount across rotations.
        // Instead, measurables are measured to the exact cell bounds and placed at appropriate coordinates.
        Layout(
            modifier = Modifier.size(containerWidth, containerHeight),
            content = {
                for (index in 0 until 4) {
                    val slot = slots.getOrNull(index)
                    if (slot != null) {
                        key(index) {
                            val slotFocusModifier = if (isStack) {
                                Modifier
                                    .fillMaxSize()
                                    .focusRequester(focusRequesters[index])
                                    .focusProperties {
                                        if (index > 0) up = focusRequesters[index - 1]
                                        if (index < 3) down = focusRequesters[index + 1]
                                    }
                            } else {
                                Modifier
                                    .fillMaxSize()
                                    .focusRequester(focusRequesters[index])
                                    .focusProperties {
                                        when (index) {
                                            0 -> {
                                                right = focusRequesters[1]
                                                down = focusRequesters[2]
                                            }
                                            1 -> {
                                                left = focusRequesters[0]
                                                down = focusRequesters[3]
                                            }
                                            2 -> {
                                                up = focusRequesters[0]
                                                right = focusRequesters[3]
                                            }
                                            3 -> {
                                                up = focusRequesters[1]
                                                left = focusRequesters[2]
                                            }
                                        }
                                    }
                            }

                            VideoSlotCard(
                                slot = slot,
                                renderVideo = { renderVideo(index) },
                                modifier = slotFocusModifier,
                                onSlotClick = { onSlotClick(index) },
                                onSlotLongClick = { onSlotLongClick(index) },
                                onSlotFocused = { onSlotSelected(index) },
                                onChannelClick = { onChannelClick(index) },
                                onMuteToggle = { onMuteToggle(index) },
                                onFocusClick = { onFocusClick(index) }
                            )
                        }
                    }
                }
            }
        ) { measurables, constraints ->
            val cellWPx = (constraints.maxWidth * (if (isStack) 1f else 0.5f)).roundToInt()
            val cellHPx = (constraints.maxHeight * (if (isStack) 0.25f else 0.5f)).roundToInt()
            val childConstraints = Constraints.fixed(cellWPx, cellHPx)

            val placeables = measurables.map { it.measure(childConstraints) }

            layout(constraints.maxWidth, constraints.maxHeight) {
                if (isStack) {
                    placeables.forEachIndexed { i, placeable ->
                        placeable.placeRelative(x = 0, y = i * cellHPx)
                    }
                } else {
                    placeables.getOrNull(0)?.placeRelative(x = 0, y = 0)
                    placeables.getOrNull(1)?.placeRelative(x = cellWPx, y = 0)
                    placeables.getOrNull(2)?.placeRelative(x = 0, y = cellHPx)
                    placeables.getOrNull(3)?.placeRelative(x = cellWPx, y = cellHPx)
                }
            }
        }
    }
}

@Composable
private fun Focus1Plus3Layout(
    slots: List<SlotState>,
    focusedIndex: Int,
    renderVideo: @Composable (Int) -> Unit,
    onSlotClick: (Int) -> Unit,
    onSlotLongClick: (Int) -> Unit,
    onSlotSelected: (Int) -> Unit,
    onChannelClick: (Int) -> Unit,
    onMuteToggle: (Int) -> Unit,
    onFocusClick: (Int) -> Unit
) {
    val primarySlot = slots[focusedIndex]
    val otherSlots = slots.filter { it.slotIndex != focusedIndex }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Primary 16:9 + 3 stacked 16:9 on side: target aspect is 64/27 (~2.37)
        val targetAspect = 64f / 27f
        val currentAspect = maxWidth / maxHeight

        val (containerWidth, containerHeight) = if (currentAspect > targetAspect) {
            Pair(maxHeight * targetAspect, maxHeight)
        } else {
            Pair(maxWidth, maxWidth / targetAspect)
        }

        Row(modifier = Modifier.size(containerWidth, containerHeight)) {
            // Primary dominant view (takes 3/4 width = 16:9)
            VideoSlotCard(
                slot = primarySlot,
                renderVideo = { renderVideo(primarySlot.slotIndex) },
                modifier = Modifier.weight(3f).fillMaxHeight(),
                onSlotClick = { onSlotClick(primarySlot.slotIndex) },
                onSlotLongClick = { onSlotLongClick(primarySlot.slotIndex) },
                onSlotFocused = { onSlotSelected(primarySlot.slotIndex) },
                onChannelClick = { onChannelClick(primarySlot.slotIndex) },
                onMuteToggle = { onMuteToggle(primarySlot.slotIndex) },
                onFocusClick = { onFocusClick(primarySlot.slotIndex) }
            )

            // Secondary sidebar (takes 1/4 width, 3 stacked 16:9 slots)
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                otherSlots.forEach { slot ->
                    VideoSlotCard(
                        slot = slot,
                        renderVideo = { renderVideo(slot.slotIndex) },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        onSlotClick = { onSlotClick(slot.slotIndex) },
                        onSlotLongClick = { onSlotLongClick(slot.slotIndex) },
                        onSlotFocused = { onSlotSelected(slot.slotIndex) },
                        onChannelClick = { onChannelClick(slot.slotIndex) },
                        onMuteToggle = { onMuteToggle(slot.slotIndex) },
                        onFocusClick = { onFocusClick(slot.slotIndex) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PipLayout(
    slots: List<SlotState>,
    focusedIndex: Int,
    renderVideo: @Composable (Int) -> Unit,
    onSlotClick: (Int) -> Unit,
    onSlotLongClick: (Int) -> Unit,
    onSlotSelected: (Int) -> Unit,
    onChannelClick: (Int) -> Unit,
    onMuteToggle: (Int) -> Unit,
    onFocusClick: (Int) -> Unit
) {
    val primarySlot = slots[focusedIndex]
    val secondaryIndex = (focusedIndex + 1) % slots.size
    val secondarySlot = slots[secondaryIndex]

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val targetAspect = 16f / 9f
        val currentAspect = maxWidth / maxHeight

        val (mainWidth, mainHeight) = if (currentAspect > targetAspect) {
            Pair(maxHeight * targetAspect, maxHeight)
        } else {
            Pair(maxWidth, maxWidth / targetAspect)
        }

        Box(modifier = Modifier.size(mainWidth, mainHeight)) {
            // Primary Fullscreen 16:9 Slot
            VideoSlotCard(
                slot = primarySlot,
                renderVideo = { renderVideo(primarySlot.slotIndex) },
                modifier = Modifier.fillMaxSize(),
                onSlotClick = { onSlotClick(primarySlot.slotIndex) },
                onSlotLongClick = { onSlotLongClick(primarySlot.slotIndex) },
                onSlotFocused = { onSlotSelected(primarySlot.slotIndex) },
                onChannelClick = { onChannelClick(primarySlot.slotIndex) },
                onMuteToggle = { onMuteToggle(primarySlot.slotIndex) },
                onFocusClick = { onFocusClick(primarySlot.slotIndex) }
            )

            // Floating Inset Secondary Slot (bottom-right)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxWidth(0.32f)
                    .aspectRatio(16f / 9f)
                    .padding(8.dp)
            ) {
                VideoSlotCard(
                    slot = secondarySlot,
                    renderVideo = { renderVideo(secondarySlot.slotIndex) },
                    modifier = Modifier.fillMaxSize(),
                    onSlotClick = { onSlotClick(secondarySlot.slotIndex) },
                    onSlotLongClick = { onSlotLongClick(secondarySlot.slotIndex) },
                    onSlotFocused = { onSlotSelected(secondarySlot.slotIndex) },
                    onChannelClick = { onChannelClick(secondarySlot.slotIndex) },
                    onMuteToggle = { onMuteToggle(secondarySlot.slotIndex) },
                    onFocusClick = { onFocusClick(secondarySlot.slotIndex) }
                )
            }
        }
    }
}

@Composable
private fun SingleFullscreenLayout(
    slot: SlotState,
    renderVideo: @Composable (Int) -> Unit,
    onSlotClick: (Int) -> Unit,
    onSlotLongClick: (Int) -> Unit,
    onChannelClick: (Int) -> Unit,
    onMuteToggle: (Int) -> Unit,
    onFocusClick: (Int) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(slot.slotIndex) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val targetAspect = 16f / 9f
        val currentAspect = maxWidth / maxHeight

        val (vidWidth, vidHeight) = if (currentAspect > targetAspect) {
            Pair(maxHeight * targetAspect, maxHeight)
        } else {
            Pair(maxWidth, maxWidth / targetAspect)
        }

        VideoSlotCard(
            slot = slot,
            renderVideo = { renderVideo(slot.slotIndex) },
            modifier = Modifier
                .size(vidWidth, vidHeight)
                .focusRequester(focusRequester),
            onSlotClick = { onSlotClick(slot.slotIndex) },
            onSlotLongClick = { onSlotLongClick(slot.slotIndex) },
            onChannelClick = { onChannelClick(slot.slotIndex) },
            onMuteToggle = { onMuteToggle(slot.slotIndex) },
            onFocusClick = { onFocusClick(slot.slotIndex) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CarouselLayout(
    slots: List<SlotState>,
    focusedIndex: Int,
    renderVideo: @Composable (Int) -> Unit,
    onSlotClick: (Int) -> Unit,
    onSlotLongClick: (Int) -> Unit,
    onSlotSelected: (Int) -> Unit,
    onChannelClick: (Int) -> Unit,
    onMuteToggle: (Int) -> Unit,
    onFocusClick: (Int) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val initialPage = focusedIndex.coerceIn(0, (slots.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { slots.size }
    )

    // Sync external focusedIndex changes into pager
    LaunchedEffect(focusedIndex) {
        if (focusedIndex in slots.indices && pagerState.currentPage != focusedIndex) {
            pagerState.animateScrollToPage(focusedIndex)
        }
    }

    // Sync user pager swipes back to focused slot (updates audio/active slot without toggling mode)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settledPage ->
            if (settledPage in slots.indices && settledPage != focusedIndex) {
                onSlotSelected(settledPage)
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val targetAspect = 16f / 9f
        val currentAspect = maxWidth / maxHeight

        // Maintain 16:9 carousel page dimension (occupying ~75% width or height)
        val (cardWidth, cardHeight) = if (currentAspect > targetAspect) {
            val h = maxHeight * 0.78f
            Pair(h * targetAspect, h)
        } else {
            val w = maxWidth * 0.78f
            Pair(w, w / targetAspect)
        }

        // Horizontal padding so adjacent carousel cards peek in from left and right
        val horizontalPadding = ((maxWidth - cardWidth) / 2f).coerceAtLeast(24.dp)

        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            pageSpacing = 16.dp,
            beyondBoundsPageCount = 3, // Keep all 4 video streams alive and actively rendering
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) { pageIndex ->
            val slot = slots.getOrNull(pageIndex)
            if (slot != null) {
                // Calculate signed offset from current scroll position
                val pageOffset = ((pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction)
                val clampedOffset = pageOffset.coerceIn(-2.5f, 2.5f)

                Box(
                    modifier = Modifier
                        .size(cardWidth, cardHeight)
                        .graphicsLayer {
                            // 3D Perspective Cover Flow transformation
                            cameraDistance = 16f * density

                            // Rotation around Y-axis (tilted inwards)
                            rotationY = -clampedOffset * 28f

                            // Scale down off-center items for 3D depth
                            val scale = (1f - (clampedOffset.absoluteValue * 0.15f)).coerceIn(0.72f, 1f)
                            scaleX = scale
                            scaleY = scale

                            // Off-center items sit slightly further in Z / alpha dimming
                            alpha = (1f - (clampedOffset.absoluteValue * 0.25f)).coerceIn(0.45f, 1f)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    VideoSlotCard(
                        slot = slot,
                        renderVideo = { renderVideo(slot.slotIndex) },
                        modifier = Modifier.fillMaxSize(),
                        onSlotClick = {
                            if (pagerState.currentPage != pageIndex) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pageIndex)
                                }
                            } else {
                                onSlotClick(slot.slotIndex)
                            }
                        },
                        onSlotLongClick = { onSlotLongClick(slot.slotIndex) },
                        onChannelClick = { onChannelClick(slot.slotIndex) },
                        onMuteToggle = { onMuteToggle(slot.slotIndex) },
                        onFocusClick = {
                            if (pagerState.currentPage != pageIndex) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pageIndex)
                                }
                            } else {
                                onFocusClick(slot.slotIndex)
                            }
                        }
                    )
                }
            }
        }

        // Slot Indicator & Quick Jumper Pills (visible when screen height allows, e.g. portrait)
        val hasRoomForIndicators = maxHeight > (cardHeight + 80.dp)
        if (hasRoomForIndicators) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                slots.forEachIndexed { idx, slot ->
                    val isCurrent = pagerState.currentPage == idx
                    Surface(
                        color = if (isCurrent) PrimaryCyan else Color(0xB30F172A),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isCurrent) PrimaryCyan else Color(0x4038BDF8)
                        ),
                        modifier = Modifier.clickable {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(idx)
                            }
                            onSlotSelected(idx)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Slot ${idx + 1}" + (slot.channel?.let { " • ${it.guideNumber}" } ?: ""),
                                color = if (isCurrent) Color.Black else TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
