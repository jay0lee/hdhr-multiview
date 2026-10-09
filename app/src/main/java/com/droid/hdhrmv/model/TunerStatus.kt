package com.droid.hdhrmv.model

data class TunerStatus(
    val resource: String,
    val vctNumber: String? = null,
    val vctName: String? = null,
    val targetIp: String? = null
) {
    val isFree: Boolean
        get() = vctNumber.isNullOrEmpty() && targetIp.isNullOrEmpty()
}
