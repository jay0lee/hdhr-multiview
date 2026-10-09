package com.droid.hdhrmv.ui

import com.droid.hdhrmv.model.Channel
import com.droid.hdhrmv.model.MultiviewMode
import com.droid.hdhrmv.model.SlotState

object MultiviewLayoutManager {

    const val MAX_SLOTS = 4

    fun createInitialSlots(): List<SlotState> {
        return (0 until MAX_SLOTS).map { index ->
            SlotState(
                slotIndex = index,
                channel = null,
                isFocused = (index == 0),
                isMuted = (index != 0),
                isPlaying = false
            )
        }
    }

    fun setFocusedSlot(slots: List<SlotState>, targetIndex: Int): List<SlotState> {
        return slots.map { slot ->
            val isTarget = (slot.slotIndex == targetIndex)
            slot.copy(
                isFocused = isTarget,
                isMuted = !isTarget
            )
        }
    }

    fun assignChannel(slots: List<SlotState>, slotIndex: Int, channel: Channel?): List<SlotState> {
        return slots.map { slot ->
            if (slot.slotIndex == slotIndex) {
                slot.copy(channel = channel)
            } else {
                slot
            }
        }
    }

    fun toggleMute(slots: List<SlotState>, slotIndex: Int): List<SlotState> {
        return slots.map { slot ->
            if (slot.slotIndex == slotIndex) {
                slot.copy(isMuted = !slot.isMuted)
            } else {
                slot
            }
        }
    }

    fun getVisibleSlotIndices(mode: MultiviewMode, focusedSlot: Int): List<Int> {
        return when (mode) {
            MultiviewMode.GRID_4 -> listOf(0, 1, 2, 3)
            MultiviewMode.FULLSCREEN -> listOf(focusedSlot)
            MultiviewMode.PIP -> {
                val secondary = (focusedSlot + 1) % MAX_SLOTS
                listOf(focusedSlot, secondary)
            }
            MultiviewMode.FOCUS_1_PLUS_3 -> {
                val others = (0 until MAX_SLOTS).filter { it != focusedSlot }
                listOf(focusedSlot) + others
            }
            MultiviewMode.CAROUSEL -> listOf(0, 1, 2, 3)
        }
    }

    enum class GridArrangement {
        GRID_2X2,
        STACK_1X4
    }

    /**
     * Determines whether a 2x2 grid or a 1x4 vertical stack maximizes total video area
     * for broadcast content with the given aspect ratio (default 16:9).
     */
    fun calculateOptimalGridArrangement(
        availableWidth: Float,
        availableHeight: Float,
        aspectRatio: Float = 16f / 9f
    ): GridArrangement {
        if (availableWidth <= 0f || availableHeight <= 0f) return GridArrangement.GRID_2X2

        // Configuration A: 2x2 grid (2 columns x 2 rows)
        val cellWidth2x2 = minOf(availableWidth / 2f, (aspectRatio * availableHeight) / 2f)
        val area2x2 = 4f * cellWidth2x2 * (cellWidth2x2 / aspectRatio)

        // Configuration B: 1x4 vertical stack (1 column x 4 rows)
        val cellWidth1x4 = minOf(availableWidth, (aspectRatio * availableHeight) / 4f)
        val area1x4 = 4f * cellWidth1x4 * (cellWidth1x4 / aspectRatio)

        return if (area1x4 > area2x2) {
            GridArrangement.STACK_1X4
        } else {
            GridArrangement.GRID_2X2
        }
    }
}
