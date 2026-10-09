package com.droid.hdhrmv.model

data class SlotState(
    val slotIndex: Int,
    val channel: Channel? = null,
    val isFocused: Boolean = false,
    val isMuted: Boolean = true,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val errorMessage: String? = null
)
