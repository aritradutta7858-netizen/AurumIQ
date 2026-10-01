package com.aurumiq.app.engine

import kotlin.math.sqrt

/**
 * Computes rolling statistics (mean, standard deviation) over a window of observations.
 * Used to establish the "normal" range of spread behavior.
 */
object RollingStatistics {

    data class RollingStat(
        val date: Long,
        val value: Double,
        val mean: Double,
        val stdDev: Double,
        val windowSize: Int,
        val dataPointsUsed: Int
    )

    /**
     * Calculate rolling mean and standard deviation.
     *
     * @param data Time-ordered list of (date, value) pairs
     * @param windowSize Number of observations in the rolling window
     * @param minDataPoints Minimum observations required before computing stats
     * @return List of RollingStat — one per data point that has sufficient history
     */
    fun calculate(
        data: List<Pair<Long, Double>>,
        windowSize: Int,
        minDataPoints: Int
    ): List<RollingStat> {
        if (data.size < minDataPoints) return emptyList()

        val results = mutableListOf<RollingStat>()

        for (i in data.indices) {
            // Look-back window: indices max(0, i-windowSize+1)..i
            val windowStart = maxOf(0, i - windowSize + 1)
            val window = data.subList(windowStart, i + 1)

            if (window.size < minDataPoints) {
                // Not enough data yet — skip but don't flag an error
                continue
            }

            val values = window.map { it.second }
            val mean = values.average()
            val variance = values.map { (it - mean) * (it - mean) }.average()
            val stdDev = sqrt(variance)

            results.add(
                RollingStat(
                    date = data[i].first,
                    value = data[i].second,
                    mean = mean,
                    stdDev = stdDev,
                    windowSize = windowSize,
                    dataPointsUsed = window.size
                )
            )
        }

        return results
    }
}

/**
 * Calculates z-scores from rolling statistics.
 *
 * Z-score = (value - mean) / stdDev
 *
 * Interpretation:
 *   |z| < 1.0   → Normal range
 *   1.0 ≤ |z| < 2.0 → Elevated but not unusual
 *   |z| ≥ 2.0   → Statistically unusual (default threshold)
 *   |z| ≥ 3.0   → Highly unusual
 */
object ZScoreCalculator {

    data class ZScorePoint(
        val date: Long,
        val value: Double,
        val mean: Double,
        val stdDev: Double,
        val zScore: Double
    )

    /**
     * Calculate z-scores from rolling statistics.
     *
     * @param stats Rolling statistics output
     * @return List of z-score points. StdDev of 0 results in z-score of 0.0.
     */
    fun calculate(stats: List<RollingStatistics.RollingStat>): List<ZScorePoint> {
        return stats.map { stat ->
            val zScore = if (stat.stdDev > 0.0) {
                (stat.value - stat.mean) / stat.stdDev
            } else {
                0.0 // All values identical — no spread deviation
            }

            ZScorePoint(
                date = stat.date,
                value = stat.value,
                mean = stat.mean,
                stdDev = stat.stdDev,
                zScore = zScore
            )
        }
    }
}
