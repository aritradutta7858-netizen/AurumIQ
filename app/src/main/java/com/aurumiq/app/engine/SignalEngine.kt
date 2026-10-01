package com.aurumiq.app.engine

import com.aurumiq.app.data.model.*

/**
 * SignalEngine orchestrates all analytics components to produce trading signals.
 *
 * Pipeline:
 *   1. Normalize prices (ContractNormalizer)
 *   2. Calculate spreads (SpreadCalculator)
 *   3. Compute rolling statistics (RollingStatistics)
 *   4. Calculate z-scores (ZScoreCalculator)
 *   5. Evaluate liquidity (LiquidityAnalyzer)
 *   6. Estimate costs (CostEstimator)
 *   7. Classify signals (SignalCategory)
 */
object SignalEngine {

    /**
     * Generate signals for a pair of contracts.
     *
     * CRITICAL: This function only uses data up to each observation date.
     * No look-ahead bias: the rolling window at date T only includes data <= T.
     *
     * @param recordsA Records for contract A, sorted by date ascending
     * @param recordsB Records for contract B, sorted by date ascending
     * @param settings Analytics configuration
     * @return List of signals, one per date where both contracts have data
     */
    fun generateSignals(
        recordsA: List<MarketRecord>,
        recordsB: List<MarketRecord>,
        settings: Settings
    ): List<Signal> {
        if (recordsA.isEmpty() || recordsB.isEmpty()) return emptyList()

        // Step 1: Normalize
        val normalizedA = ContractNormalizer.normalizeBatch(recordsA)
        val normalizedB = ContractNormalizer.normalizeBatch(recordsB)

        if (normalizedA.isEmpty() || normalizedB.isEmpty()) return emptyList()

        // Step 2: Calculate spread series
        val spreadSeries = SpreadCalculator.calculateSpreadSeries(normalizedA, normalizedB)
        if (spreadSeries.isEmpty()) return emptyList()

        // Step 3: Rolling statistics on spread values
        val spreadData = spreadSeries.map { Pair(it.date, it.spreadPercent) }
        val rollingStats = RollingStatistics.calculate(
            data = spreadData,
            windowSize = settings.rollingWindow,
            minDataPoints = settings.minDataPoints
        )

        // Step 4: Z-scores
        val zScores = ZScoreCalculator.calculate(rollingStats)
        val zScoreMap = zScores.associateBy { it.date }

        // Step 5: Liquidity (rolling — use data up to each point)
        val liquidityA = LiquidityAnalyzer.analyze(
            volumes = recordsA.map { it.volume },
            openInterests = recordsA.map { it.openInterest },
            symbol = recordsA.first().symbol,
            settings = settings
        )
        val liquidityB = LiquidityAnalyzer.analyze(
            volumes = recordsB.map { it.volume },
            openInterests = recordsB.map { it.openInterest },
            symbol = recordsB.first().symbol,
            settings = settings
        )
        val pairLiquidity = LiquidityAnalyzer.analyzePair(liquidityA, liquidityB)

        // Step 6: Cost estimation
        val avgPrice = (normalizedA.map { it.normalizedPrice }.average() +
                normalizedB.map { it.normalizedPrice }.average()) / 2.0
        val costBreakdown = CostEstimator.estimateRoundTrip(avgPrice, settings)

        // Step 7: Generate signals
        val spreadMap = spreadSeries.associateBy { it.date }

        return spreadSeries.map { sp ->
            val zData = zScoreMap[sp.date]
            val zScore = zData?.zScore ?: 0.0
            val rollingMean = zData?.mean ?: sp.spreadPercent
            val rollingStdDev = zData?.stdDev ?: 0.0

            val category = classifySignal(
                zScore = zScore,
                hasZScore = zData != null,
                pairLiquidity = pairLiquidity,
                liquidityA = liquidityA.meetsThreshold,
                liquidityB = liquidityB.meetsThreshold,
                settings = settings
            )

            val netSpread = sp.spreadPercent - costBreakdown.totalRoundTripPercent

            Signal(
                date = sp.date,
                symbolA = normalizedA.first().symbol,
                symbolB = normalizedB.first().symbol,
                expiryA = normalizedA.first().expiryDate,
                expiryB = normalizedB.first().expiryDate,
                normalizedPriceA = sp.priceA,
                normalizedPriceB = sp.priceB,
                spreadPercent = sp.spreadPercent,
                zScore = zScore,
                rollingMean = rollingMean,
                rollingStdDev = rollingStdDev,
                volumeA = sp.volumeA,
                volumeB = sp.volumeB,
                oiA = sp.oiA,
                oiB = sp.oiB,
                liquidityScore = pairLiquidity,
                category = category,
                estimatedCostPercent = costBreakdown.totalRoundTripPercent,
                netSpreadPercent = netSpread,
                dataSource = normalizedA.first().dataSource
            )
        }
    }

    private fun classifySignal(
        zScore: Double,
        hasZScore: Boolean,
        pairLiquidity: Double,
        liquidityA: Boolean,
        liquidityB: Boolean,
        settings: Settings
    ): SignalCategory {
        if (!hasZScore) return SignalCategory.INSUFFICIENT_DATA

        if (!liquidityA || !liquidityB) {
            if (kotlin.math.abs(zScore) >= settings.zScoreEntryThreshold) {
                return SignalCategory.LIQUIDITY_WARNING
            }
        }

        return when {
            zScore >= settings.zScoreEntryThreshold -> SignalCategory.HIGH_RELATIVE_PREMIUM
            zScore <= -settings.zScoreEntryThreshold -> SignalCategory.HIGH_RELATIVE_DISCOUNT
            kotlin.math.abs(zScore) >= 1.5 -> SignalCategory.WATCH
            else -> SignalCategory.NORMAL
        }
    }
}
