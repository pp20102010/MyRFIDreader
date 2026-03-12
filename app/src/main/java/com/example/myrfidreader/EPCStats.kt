package com.example.myrfidreader

data class EPCStats(
    val epc: String,
    var totalCount: Int = 0,
    var sumCount: Int = 0,
    var sumSquares: Double = 0.0,
    var minCount: Int = Int.MAX_VALUE,
    var successfulIntervals: Int = 0,          // количество интервалов, где был EPC
    var totalRssi: Int = 0,
    var rssiCount: Int = 0,
    var sumRssiSquares: Double = 0.0            // для стандартного отклонения RSSI
) {
    val avgRssi: Double get() = if (rssiCount > 0) totalRssi.toDouble() / rssiCount else 0.0
    fun avgCount(intervals: Int): Double = if (intervals > 0) sumCount.toDouble() / intervals else 0.0
    fun stdDev(intervals: Int): Double {
        if (intervals <= 1) return 0.0
        val mean = avgCount(intervals)
        return Math.sqrt((sumSquares / intervals) - (mean * mean))
    }
    fun stdDevRssi(): Double {
        if (rssiCount <= 1) return 0.0
        val mean = avgRssi
        return Math.sqrt((sumRssiSquares / rssiCount) - (mean * mean))
    }
}