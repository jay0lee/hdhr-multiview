package com.droid.hdhrmv.model

enum class MultiviewMode(val displayName: String, val maxVisibleSlots: Int) {
    GRID_4("Grid (Auto)", 4),
    FOCUS_1_PLUS_3("1 + 3 Focus", 4),
    PIP("Picture-in-Picture", 2),
    FULLSCREEN("Single View", 1),
    CAROUSEL("Quick Slide / Carousel", 4)
}
