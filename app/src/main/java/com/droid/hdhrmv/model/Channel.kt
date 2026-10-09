package com.droid.hdhrmv.model

data class Channel(
    val guideNumber: String,
    val guideName: String,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val isHd: Boolean = true,
    val streamUrl: String = "",
    val favorite: Boolean = false,
    val signalStrength: Int? = null,
    val signalQuality: Int? = null
)
