package com.aurumiq.app.data.demo

import com.aurumiq.app.data.model.ContractSpec
import com.aurumiq.app.data.model.DataSource
import com.aurumiq.app.data.model.MarketRecord
import java.util.Calendar
import java.util.Random
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Generates clearly-labeled SYNTHETIC data for demonstration purposes.
 *
 * ═══════════════════════════════════════════════════════════════
 * THIS IS NOT REAL MARKET DATA.
 * ALL GENERATED DATA IS TAGGED WITH DataSource.DEMO.
 * ═══════════════════════════════════════════════════════════════
 *
 * The generator creates realistic-looking MCX gold futures data with:
 *   - Correlated price movements across contracts
 *   - Realistic volume and open interest patterns
 *   - Multiple expiry dates per contract
 *   - Some intentional relative deviations (to demonstrate signal detection)
 *   - Near-expiry volume decay
 */
object DemoDataGenerator {

    private val random = Random(42) // Fixed seed for reproducibility

    private fun nextGaussian(): Double = random.nextGaussian()
    private fun nextInt(from: Int, until: Int): Int = from + random.nextInt(until - from)

    /**
     * Generate a full demo dataset.
     *
     * @param tradingDays Number of trading days to generate
     * @return List of MarketRecord tagged as DEMO
     */
    fun generate(tradingDays: Int = 500): List<MarketRecord> {
        val records = mutableListOf<MarketRecord>()
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

        // Start date: 2024-01-02 (a Tuesday)
        calendar.set(2024, Calendar.JANUARY, 2, 0, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // Generate expiry dates (last Thursday of each month)
        val expiryDates = generateExpiryDates(calendar.clone() as Calendar, tradingDays + 90)

        // Base gold price in INR per 10g (999 purity basis) — starting around ₹62,000
        var baseGoldPrice = 62000.0

        var day = 0
        val tradingDatesList = mutableListOf<Long>()

        while (day < tradingDays) {
            // Skip weekends
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
                continue
            }

            val currentDate = calendar.timeInMillis
            tradingDatesList.add(currentDate)

            // Evolve base price (random walk with slight upward drift)
            val dailyReturn = (nextGaussian() * 0.008) + 0.0001 // ~0.8% daily vol, slight drift
            baseGoldPrice *= (1.0 + dailyReturn)

            // Add some cyclical patterns
            val cyclical = sin(day * 0.05) * 50 + cos(day * 0.02) * 30

            val todayBasePrice = baseGoldPrice + cyclical

            // Generate records for each contract type
            for (spec in ContractSpec.ALL) {
                // Find active expiry for this contract on this date
                val activeExpiry = findActiveExpiry(currentDate, expiryDates)
                    ?: continue

                val daysToExpiry = ((activeExpiry - currentDate) / (24 * 60 * 60 * 1000)).toInt()
                if (daysToExpiry < 0) continue

                // Contract-specific deviation from base price
                val contractDeviation = generateContractDeviation(spec.symbol, day, daysToExpiry)

                // Convert from 999/10g basis to contract's quotation basis
                val purityFactor = spec.purityPartsPerThousand.toDouble() / 999.0
                val quotationFactor = spec.quotationBasisGrams / 10.0
                val rawPrice = (todayBasePrice + contractDeviation) * purityFactor * quotationFactor

                // OHLC generation
                val intraday = abs(nextGaussian() * rawPrice * 0.005) + (rawPrice * 0.001)
                val open = maxOf(0.01, rawPrice + (nextGaussian() * rawPrice * 0.002))
                val close = maxOf(0.01, rawPrice + (nextGaussian() * rawPrice * 0.001))
                val high = max(open, close) + intraday
                val low = maxOf(0.01, min(open, close) - intraday)
                val settlement = maxOf(0.01, close + (nextGaussian() * rawPrice * 0.0005))

                // Volume — inversely proportional to lot size, decays near expiry
                val baseVolume = when (spec.symbol) {
                    "GOLDM" -> nextInt(500, 3000)
                    "GOLDTEN" -> nextInt(200, 1500)
                    "GOLDGUINEA" -> nextInt(100, 800)
                    "GOLDPETAL" -> nextInt(50, 500)
                    else -> nextInt(100, 500)
                }
                val expiryDecay = if (daysToExpiry < 5) 0.3 else if (daysToExpiry < 15) 0.7 else 1.0
                val volume = (baseVolume * expiryDecay).toLong()

                // Open interest — builds up then decays near expiry
                val oiBuild = if (daysToExpiry > 20) 1.0 else daysToExpiry / 20.0
                val baseOI = when (spec.symbol) {
                    "GOLDM" -> nextInt(2000, 10000)
                    "GOLDTEN" -> nextInt(1000, 5000)
                    "GOLDGUINEA" -> nextInt(500, 3000)
                    "GOLDPETAL" -> nextInt(200, 1500)
                    else -> nextInt(200, 1000)
                }
                val oi = (baseOI * oiBuild).toLong()

                records.add(
                    MarketRecord(
                        symbol = spec.symbol,
                        date = currentDate,
                        expiryDate = activeExpiry,
                        open = maxOf(0.01, open),
                        high = maxOf(0.01, high),
                        low = maxOf(0.01, low),
                        close = maxOf(0.01, close),
                        volume = maxOf(1, volume),
                        openInterest = maxOf(1, oi),
                        settlementPrice = maxOf(0.01, settlement),
                        dataSource = DataSource.DEMO
                    )
                )
            }

            calendar.add(Calendar.DAY_OF_MONTH, 1)
            day++
        }

        return records
    }

    /**
     * Generate contract-specific deviations that create spread opportunities.
     *
     * Intentionally introduces:
     *   - Small persistent basis between 995 and 999 purity contracts
     *   - Occasional larger deviations (for signal generation)
     *   - Mean-reverting behavior (deviations tend to correct)
     */
    private fun generateContractDeviation(symbol: String, day: Int, daysToExpiry: Int): Double {
        // Base deviation per contract (persistent small difference)
        val baseBias = when (symbol) {
            "GOLDM" -> 15.0    // Slightly higher due to 995 purity adjustment noise
            "GOLDTEN" -> 0.0   // Reference contract
            "GOLDGUINEA" -> -8.0  // Slightly lower due to smaller lot
            "GOLDPETAL" -> -20.0  // Lowest liquidity → slightly discounted
            else -> 0.0
        }

        // Random noise
        val noise = nextGaussian() * 25.0

        // Occasional regime shifts (creates tradeable signals)
        val regimeShift = when {
            day % 73 in 0..8 && symbol == "GOLDM" -> 120.0 * sin(day * 0.3)
            day % 61 in 0..6 && symbol == "GOLDPETAL" -> -90.0 * cos(day * 0.4)
            day % 89 in 0..5 && symbol == "GOLDGUINEA" -> 80.0 * sin(day * 0.2)
            else -> 0.0
        }

        // Expiry convergence: deviations shrink near expiry
        val expiryConvergence = if (daysToExpiry < 10) {
            (10 - daysToExpiry) / 10.0 * -0.5
        } else 0.0

        return baseBias + noise + regimeShift + (baseBias * expiryConvergence)
    }

    /**
     * Generate last-Thursday-of-month expiry dates.
     */
    private fun generateExpiryDates(startCal: Calendar, daysAhead: Int): List<Long> {
        val expiries = mutableListOf<Long>()
        val cal = startCal.clone() as Calendar

        // Go back 1 month to capture expiries before our start
        cal.add(Calendar.MONTH, -1)

        val endCal = startCal.clone() as Calendar
        endCal.add(Calendar.DAY_OF_MONTH, daysAhead)

        while (cal.before(endCal)) {
            // Find last Thursday of current month
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.THURSDAY) {
                cal.add(Calendar.DAY_OF_MONTH, -1)
            }
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            expiries.add(cal.timeInMillis)

            // Move to next month
            cal.add(Calendar.MONTH, 1)
            cal.set(Calendar.DAY_OF_MONTH, 1)
        }

        return expiries.sorted()
    }

    /**
     * Find the nearest future expiry date.
     */
    private fun findActiveExpiry(currentDate: Long, expiries: List<Long>): Long? {
        return expiries.firstOrNull { it >= currentDate }
    }
}
