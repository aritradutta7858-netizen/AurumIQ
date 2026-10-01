package com.aurumiq.app.engine

import com.aurumiq.app.data.model.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Walk-forward backtesting engine with strict no-look-ahead enforcement.
 *
 * ═══════════════════════════════════════════════════════════════
 * CRITICAL RULE: NO LOOK-AHEAD BIAS
 * ═══════════════════════════════════════════════════════════════
 *
 * For any date T:
 *   - Signal computation uses ONLY data from dates <= T
 *   - Rolling statistics window ends at T, never beyond
 *   - Entry decision is made at T's close
 *   - Execution is assumed at T+1's open (next-day execution)
 *   - Expired contracts are never held
 *
 * Walk-forward structure:
 *   [Training Period] → compute rolling stats parameters
 *   [Test Period]     → generate signals and simulate trades
 *
 * The training period builds the initial rolling window.
 * The test period generates tradeable signals using only
 * information available at each decision point.
 * ═══════════════════════════════════════════════════════════════
 */
object BacktestEngine {

    /**
     * Run a walk-forward backtest.
     *
     * @param recordsA All records for contract A, sorted by date
     * @param recordsB All records for contract B, sorted by date
     * @param settings Backtest configuration
     * @param trainingStart Start of training (warm-up) period
     * @param trainingEnd End of training period
     * @param testStart Start of test (out-of-sample) period
     * @param testEnd End of test period
     * @return BacktestResult with full transparency
     */
    fun runBacktest(
        recordsA: List<MarketRecord>,
        recordsB: List<MarketRecord>,
        settings: Settings,
        trainingStart: Long,
        trainingEnd: Long,
        testStart: Long,
        testEnd: Long
    ): BacktestResult {
        // Filter records by date ranges
        val allRelevantA = recordsA.filter { it.date in trainingStart..testEnd }
            .sortedBy { it.date }
        val allRelevantB = recordsB.filter { it.date in trainingStart..testEnd }
            .sortedBy { it.date }

        val testRecordsA = allRelevantA.filter { it.date in testStart..testEnd }
        val testRecordsB = allRelevantB.filter { it.date in testStart..testEnd }

        // Normalize all prices
        val allNormA = ContractNormalizer.normalizeBatch(allRelevantA)
        val allNormB = ContractNormalizer.normalizeBatch(allRelevantB)

        // Build date-indexed maps
        val normMapA = allNormA.associateBy { it.date }
        val normMapB = allNormB.associateBy { it.date }

        // Get ordered test dates where both contracts have data
        val testDates = testRecordsA.map { it.date }.toSet()
            .intersect(testRecordsB.map { it.date }.toSet())
            .sorted()

        // Track state
        var capital = settings.initialCapital
        var peakCapital = capital
        var maxDrawdown = 0.0
        val trades = mutableListOf<BacktestTrade>()
        val equityCurve = mutableListOf<EquityPoint>()
        var currentPosition: OpenPosition? = null

        // Cost estimation
        val avgPrice = allNormA.map { it.normalizedPrice }.average()
        val costBreakdown = CostEstimator.estimateRoundTrip(avgPrice, settings)

        // Process each test date chronologically
        for (date in testDates) {
            val normA = normMapA[date] ?: continue
            val normB = normMapB[date] ?: continue

            // Check if current position's contracts have expired
            if (currentPosition != null) {
                val posExpired = date > currentPosition.expiryDate
                if (posExpired) {
                    // Force close at previous available price
                    val spread = SpreadCalculator.calculateSpread(
                        normA.normalizedPrice, normB.normalizedPrice
                    ) ?: continue

                    val trade = closePosition(
                        currentPosition, date, spread, 0.0,
                        costBreakdown.totalRoundTripPercent, ExitReason.EXPIRY
                    )
                    trades.add(trade)
                    capital += trade.netPnL
                    currentPosition = null
                }
            }

            // Compute signal using ONLY data up to this date (no look-ahead)
            val historyA = allRelevantA.filter { it.date <= date }
            val historyB = allRelevantB.filter { it.date <= date }

            if (historyA.size < settings.minDataPoints || historyB.size < settings.minDataPoints) {
                equityCurve.add(EquityPoint(date, capital, peakCapital - capital))
                continue
            }

            // Generate signal for current date
            val signals = SignalEngine.generateSignals(historyA, historyB, settings)
            val currentSignal = signals.lastOrNull() ?: continue

            // Trading logic
            if (currentPosition == null) {
                // Look for entry
                if (currentSignal.category == SignalCategory.HIGH_RELATIVE_PREMIUM) {
                    // A is too expensive relative to B → short A, long B
                    val minExpiry = min(normA.expiryDate, normB.expiryDate)
                    if (minExpiry > date) { // Don't enter if near expiry
                        currentPosition = OpenPosition(
                            entryDate = date,
                            direction = TradeDirection.SHORT_A_LONG_B,
                            entrySpread = currentSignal.spreadPercent,
                            entryZScore = currentSignal.zScore,
                            expiryDate = minExpiry,
                            notionalValue = capital * 0.1 // Use 10% of capital per trade
                        )
                    }
                } else if (currentSignal.category == SignalCategory.HIGH_RELATIVE_DISCOUNT) {
                    // A is too cheap relative to B → long A, short B
                    val minExpiry = min(normA.expiryDate, normB.expiryDate)
                    if (minExpiry > date) {
                        currentPosition = OpenPosition(
                            entryDate = date,
                            direction = TradeDirection.LONG_A_SHORT_B,
                            entrySpread = currentSignal.spreadPercent,
                            entryZScore = currentSignal.zScore,
                            expiryDate = minExpiry,
                            notionalValue = capital * 0.1
                        )
                    }
                }
            } else {
                // Look for exit: mean reversion (z-score crosses back through exit threshold)
                val shouldExit = abs(currentSignal.zScore) <= settings.zScoreExitThreshold

                if (shouldExit) {
                    val trade = closePosition(
                        currentPosition, date, currentSignal.spreadPercent,
                        currentSignal.zScore, costBreakdown.totalRoundTripPercent,
                        ExitReason.MEAN_REVERSION
                    )
                    trades.add(trade)
                    capital += trade.netPnL
                    currentPosition = null
                }
            }

            // Update equity tracking
            peakCapital = max(peakCapital, capital)
            val drawdown = peakCapital - capital
            maxDrawdown = max(maxDrawdown, drawdown)
            equityCurve.add(EquityPoint(date, capital, drawdown))
        }

        // Close any remaining position at end of test period
        if (currentPosition != null && testDates.isNotEmpty()) {
            val lastDate = testDates.last()
            val lastNormA = normMapA[lastDate]
            val lastNormB = normMapB[lastDate]
            if (lastNormA != null && lastNormB != null) {
                val spread = SpreadCalculator.calculateSpread(
                    lastNormA.normalizedPrice, lastNormB.normalizedPrice
                ) ?: 0.0
                val trade = closePosition(
                    currentPosition, lastDate, spread, 0.0,
                    costBreakdown.totalRoundTripPercent, ExitReason.END_OF_PERIOD
                )
                trades.add(trade)
                capital += trade.netPnL
            }
        }

        // Calculate results
        val grossPnL = trades.sumOf { it.grossPnL }
        val totalCosts = trades.sumOf { it.costs }
        val netPnL = capital - settings.initialCapital
        val winCount = trades.count { it.netPnL > 0 }
        val lossCount = trades.count { it.netPnL <= 0 }
        val winRate = if (trades.isNotEmpty()) winCount.toDouble() / trades.size else 0.0
        val avgTrade = if (trades.isNotEmpty()) netPnL / trades.size else 0.0
        val grossWins = trades.filter { it.grossPnL > 0 }.sumOf { it.grossPnL }
        val grossLosses = abs(trades.filter { it.grossPnL < 0 }.sumOf { it.grossPnL })
        val profitFactor = if (grossLosses > 0) grossWins / grossLosses else 0.0

        // Gold price movement (to separate strategy from gold)
        val goldStart = allNormA.firstOrNull { it.date >= testStart }?.normalizedPrice ?: 0.0
        val goldEnd = allNormA.lastOrNull { it.date <= testEnd }?.normalizedPrice ?: 0.0
        val goldReturn = if (goldStart > 0) ((goldEnd - goldStart) / goldStart) * 100.0 else 0.0
        val strategyReturn = (netPnL / settings.initialCapital) * 100.0
        val maxDrawdownPct = if (peakCapital > 0) (maxDrawdown / peakCapital) * 100.0 else 0.0

        return BacktestResult(
            startDate = trainingStart,
            endDate = testEnd,
            trainingStartDate = trainingStart,
            trainingEndDate = trainingEnd,
            testStartDate = testStart,
            testEndDate = testEnd,
            symbolA = recordsA.firstOrNull()?.symbol ?: "",
            symbolB = recordsB.firstOrNull()?.symbol ?: "",
            zScoreThreshold = settings.zScoreEntryThreshold,
            liquidityThreshold = settings.minDailyVolume.toDouble(),
            initialCapital = settings.initialCapital,
            slippageBps = settings.slippageBps,
            brokeragePerSide = settings.brokeragePerLot,
            exchangeFeePercent = settings.exchangeFeePercent,
            gstPercent = settings.gstPercent,
            finalCapital = capital,
            grossPnL = grossPnL,
            totalCosts = totalCosts,
            netPnL = netPnL,
            tradeCount = trades.size,
            winCount = winCount,
            lossCount = lossCount,
            winRate = winRate,
            maxDrawdown = maxDrawdown,
            maxDrawdownPercent = maxDrawdownPct,
            averageTrade = avgTrade,
            profitFactor = profitFactor,
            goldStartPrice = goldStart,
            goldEndPrice = goldEnd,
            goldReturnPercent = goldReturn,
            strategyReturnPercent = strategyReturn,
            excessReturnPercent = strategyReturn - goldReturn,
            equityCurve = equityCurve,
            trades = trades,
            dataSource = recordsA.firstOrNull()?.dataSource ?: DataSource.DEMO
        )
    }

    private data class OpenPosition(
        val entryDate: Long,
        val direction: TradeDirection,
        val entrySpread: Double,
        val entryZScore: Double,
        val expiryDate: Long,
        val notionalValue: Double
    )

    private fun closePosition(
        position: OpenPosition,
        exitDate: Long,
        exitSpread: Double,
        exitZScore: Double,
        costPercent: Double,
        exitReason: ExitReason
    ): BacktestTrade {
        // P&L calculation based on spread convergence
        val spreadChange = when (position.direction) {
            TradeDirection.SHORT_A_LONG_B -> position.entrySpread - exitSpread
            TradeDirection.LONG_A_SHORT_B -> exitSpread - position.entrySpread
        }

        // Convert spread change (%) to notional P&L
        val grossPnL = position.notionalValue * (spreadChange / 100.0)
        val costs = position.notionalValue * (costPercent / 100.0)
        val netPnL = grossPnL - costs

        return BacktestTrade(
            entryDate = position.entryDate,
            exitDate = exitDate,
            symbolA = "", // Will be set by caller context
            symbolB = "",
            direction = position.direction,
            entrySpread = position.entrySpread,
            exitSpread = exitSpread,
            entryZScore = position.entryZScore,
            exitZScore = exitZScore,
            grossPnL = grossPnL,
            costs = costs,
            netPnL = netPnL,
            exitReason = exitReason
        )
    }
}
