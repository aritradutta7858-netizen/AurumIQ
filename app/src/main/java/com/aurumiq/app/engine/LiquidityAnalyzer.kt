package com.aurumiq.app.engine

import com.aurumiq.app.data.model.Settings

/**
 * Evaluates whether contracts have sufficient liquidity for meaningful analysis.
 *
 * Liquidity score is a composite of:
 *   - Average daily volume (relative to threshold)
 *   - Open interest (relative to threshold)
 *
 * Score range: 0.0 (illiquid) to 1.0 (highly liquid)
 */
object LiquidityAnalyzer {

    data class LiquidityResult(
        val symbol: String,
        val avgVolume: Double,
        val avgOpenInterest: Double,
        val liquidityScore: Double,
        val meetsThreshold: Boolean,
        val volumeRatio: Double,
        val oiRatio: Double
    )

    /**
     * Analyze liquidity for a series of market data.
     *
     * @param volumes List of daily volumes
     * @param openInterests List of daily open interests
     * @param symbol Contract symbol
     * @param settings Configuration with thresholds
     * @return LiquidityResult
     */
    fun analyze(
        volumes: List<Long>,
        openInterests: List<Long>,
        symbol: String,
        settings: Settings
    ): LiquidityResult {
        if (volumes.isEmpty()) {
            return LiquidityResult(symbol, 0.0, 0.0, 0.0, false, 0.0, 0.0)
        }

        val avgVol = volumes.map { it.toDouble() }.average()
        val avgOI = openInterests.map { it.toDouble() }.average()

        val volumeRatio = if (settings.minDailyVolume > 0) {
            (avgVol / settings.minDailyVolume).coerceAtMost(1.0)
        } else 1.0

        val oiRatio = if (settings.minOpenInterest > 0) {
            (avgOI / settings.minOpenInterest).coerceAtMost(1.0)
        } else 1.0

        // Composite score: geometric mean of volume and OI ratios
        val score = kotlin.math.sqrt(volumeRatio * oiRatio)
        val meetsThreshold = avgVol >= settings.minDailyVolume && avgOI >= settings.minOpenInterest

        return LiquidityResult(
            symbol = symbol,
            avgVolume = avgVol,
            avgOpenInterest = avgOI,
            liquidityScore = score,
            meetsThreshold = meetsThreshold,
            volumeRatio = volumeRatio,
            oiRatio = oiRatio
        )
    }

    /**
     * Pair-level liquidity: minimum of the two legs.
     */
    fun analyzePair(
        resultA: LiquidityResult,
        resultB: LiquidityResult
    ): Double {
        return minOf(resultA.liquidityScore, resultB.liquidityScore)
    }
}
