package com.droid.hdhrmv.ui

import com.droid.hdhrmv.model.MultiviewMode
import com.droid.hdhrmv.model.SlotState
import com.droid.hdhrmv.model.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiviewLayoutManagerTest {

    private val sampleChannel1 = Channel("3.1", "KYW-TV", "MPEG2", "AC3", true, "http://10.1.0.4:5004/auto/v3.1")
    private val sampleChannel2 = Channel("10.1", "WCAU-TV", "MPEG2", "AC3", true, "http://10.1.0.4:5004/auto/v10.1")
    private val sampleChannel3 = Channel("12.1", "WHYY", "MPEG2", "AC3", true, "http://10.1.0.4:5004/auto/v12.1")
    private val sampleChannel4 = Channel("17.1", "WPHL-DT", "MPEG2", "AC3", true, "http://10.1.0.4:5004/auto/v17.1")

    @Test
    fun initialSlots_creates4SlotsWithSlot0FocusedAndUnmuted() {
        val initialSlots = MultiviewLayoutManager.createInitialSlots()
        assertEquals(4, initialSlots.size)
        assertEquals(0, initialSlots[0].slotIndex)
        assertTrue(initialSlots[0].isFocused)
        assertFalse(initialSlots[0].isMuted)

        for (i in 1 until 4) {
            assertEquals(i, initialSlots[i].slotIndex)
            assertFalse(initialSlots[i].isFocused)
            assertTrue(initialSlots[i].isMuted)
        }
    }

    @Test
    fun setFocusedSlot_unmutesFocusedAndMutesOthers() {
        val slots = MultiviewLayoutManager.createInitialSlots()
        val updated = MultiviewLayoutManager.setFocusedSlot(slots, targetIndex = 2)

        assertTrue(updated[2].isFocused)
        assertFalse(updated[2].isMuted)

        assertFalse(updated[0].isFocused)
        assertTrue(updated[0].isMuted)
        assertTrue(updated[1].isMuted)
        assertTrue(updated[3].isMuted)
    }

    @Test
    fun assignChannelToSlot_updatesSpecifiedSlot() {
        val slots = MultiviewLayoutManager.createInitialSlots()
        val updated = MultiviewLayoutManager.assignChannel(slots, slotIndex = 1, channel = sampleChannel2)

        assertEquals(sampleChannel2, updated[1].channel)
        assertNull(updated[0].channel)
    }

    @Test
    fun getVisibleSlots_grid4_showsAll4Slots() {
        val visible = MultiviewLayoutManager.getVisibleSlotIndices(MultiviewMode.GRID_4, focusedSlot = 0)
        assertEquals(listOf(0, 1, 2, 3), visible)
    }

    @Test
    fun getVisibleSlots_fullscreen_showsOnlyFocusedSlot() {
        val visible = MultiviewLayoutManager.getVisibleSlotIndices(MultiviewMode.FULLSCREEN, focusedSlot = 2)
        assertEquals(listOf(2), visible)
    }

    @Test
    fun getVisibleSlots_pip_showsFocusedAndNextSlot() {
        val visible = MultiviewLayoutManager.getVisibleSlotIndices(MultiviewMode.PIP, focusedSlot = 1)
        assertEquals(listOf(1, 2), visible)
    }

    @Test
    fun getVisibleSlots_focus1Plus3_showsAll4Slots() {
        val visible = MultiviewLayoutManager.getVisibleSlotIndices(MultiviewMode.FOCUS_1_PLUS_3, focusedSlot = 3)
        assertEquals(listOf(3, 0, 1, 2), visible)
    }

    @Test
    fun calculateOptimalGridArrangement_portraitPhone_selectsStack1x4() {
        // OnePlus 13 in portrait: 1080w x 2376h
        val arrangement = MultiviewLayoutManager.calculateOptimalGridArrangement(
            availableWidth = 1080f,
            availableHeight = 2376f
        )
        assertEquals(MultiviewLayoutManager.GridArrangement.STACK_1X4, arrangement)
    }

    @Test
    fun calculateOptimalGridArrangement_landscapePhone_selectsGrid2x2() {
        // OnePlus 13 in landscape: 2376w x 1080h
        val arrangement = MultiviewLayoutManager.calculateOptimalGridArrangement(
            availableWidth = 2376f,
            availableHeight = 1080f
        )
        assertEquals(MultiviewLayoutManager.GridArrangement.GRID_2X2, arrangement)
    }

    @Test
    fun calculateOptimalGridArrangement_tv16x9_selectsGrid2x2() {
        // 16:9 Google TV: 1920w x 1080h
        val arrangement = MultiviewLayoutManager.calculateOptimalGridArrangement(
            availableWidth = 1920f,
            availableHeight = 1080f
        )
        assertEquals(MultiviewLayoutManager.GridArrangement.GRID_2X2, arrangement)
    }
}
