package com.aurumiq.app.engine

import com.aurumiq.app.data.model.NormalizedPrice

/**
 * Calculates the percentage spread between two normalized prices.
 *
 * Spread% = (PriceA - PriceB) / PriceB × 100
 *
 * A positive spread means A is trading at a premium to B.
 * A negative spread means A is trading at a discount to B.
 */
object SpreadCalculator {

    /**
     * Calculate spread percentage between two normalized prices on the same date.
     *
     * @param priceA Normalized price of contract A
     * @param priceB Normalized price of contract B (reference)
     * @return Spread as a percentage, or null if priceB is zero
     */
    fun calculateSpread(priceA: Double, priceB: Double): Double? {
        if (priceB == 0.0) return null
        return ((priceA - priceB) / priceB) * 100.0
    }

    /**
     * Calculate spread time series for a pair of contracts.
     * Only includes dates where both contracts have data.
     *
     * @return List of (date, spreadPercent) pairs
     */
    fun calculateSpreadSeries(
        seriesA: List<NormalizedPrice>,
        seriesB: List<NormalizedPrice>
    ): List<SpreadPoint> {
        val mapB = seriesB.associateBy { it.date }

        return seriesA.mapNotNull { a ->
            val b = mapB[a.date] ?: return@mapNotNull null
            val spread = calculateSpread(a.normalizedPrice, b.normalizedPrice) ?: return@mapNotNull null
            SpreadPoint(
                date = a.date,
                priceA = a.normalizedPrice,
                priceB = b.normalizedPrice,
                spreadPercent = spread,
                volumeA = a.volume,
                volumeB = b.volume,
                oiA = a.openInterest,
                oiB = b.openInterest
            )
        }
    }
}

data class SpreadPoint(
    val date: Long,
    val priceA: Double,
    val priceB: Double,
    val spreadPercent: Double,
    val volumeA: Long,
    val volumeB: Long,
    val oiA: Long,
    val oiB: Long
)
