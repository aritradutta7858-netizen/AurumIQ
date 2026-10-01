package com.aurumiq.app.data.model

/**
 * Comprehensive backtest results with full transparency.
 * Every metric is derived from actual trade records — nothing fabricated.
 */
data class BacktestResult(
    // Configuration
    val startDate: Long,
    val endDate: Long,
    val trainingStartDate: Long,
    val trainingEndDate: Long,
    val testStartDate: Long,
    val testEndDate: Long,
    val symbolA: String,
    val symbolB: String,
    val zScoreThreshold: Double,
    val liquidityThreshold: Double,
    val initialCapital: Double,

    // Cost assumptions (documented)
    val slippageBps: Double,          // Slippage in basis points
    val brokeragePerSide: Double,     // INR per lot per side
    val exchangeFeePercent: Double,   // Exchange transaction charge %
    val gstPercent: Double,           // GST on brokerage + exchange fees

    // Results
    val finalCapital: Double,
    val grossPnL: Double,
    val totalCosts: Double,
    val netPnL: Double,
    val tradeCount: Int,
    val winCount: Int,
    val lossCount: Int,
    val winRate: Double,              // winCount / tradeCount
    val maxDrawdown: Double,          // Maximum peak-to-trough decline
    val maxDrawdownPercent: Double,
    val averageTrade: Double,         // netPnL / tradeCount
    val profitFactor: Double,         // grossWins / grossLosses

    // Gold movement (to separate strategy from gold price)
    val goldStartPrice: Double,
    val goldEndPrice: Double,
    val goldReturnPercent: Double,
    val strategyReturnPercent: Double,
    val excessReturnPercent: Double,  // strategy - gold return

    // Equity curve data points
    val equityCurve: List<EquityPoint>,
    val trades: List<BacktestTrade>,

    // Data label
    val dataSource: DataSource
)

data class EquityPoint(
    val date: Long,
    val equity: Double,
    val drawdown: Double
)

data class BacktestTrade(
    val entryDate: Long,
    val exitDate: Long,
    val symbolA: String,
    val symbolB: String,
    val direction: TradeDirection,  // LONG_A_SHORT_B or SHORT_A_LONG_B
    val entrySpread: Double,
    val exitSpread: Double,
    val entryZScore: Double,
    val exitZScore: Double,
    val grossPnL: Double,
    val costs: Double,
    val netPnL: Double,
    val exitReason: ExitReason
)

enum class TradeDirection(val displayName: String) {
    LONG_A_SHORT_B("Long A / Short B"),
    SHORT_A_LONG_B("Short A / Long B")
}

enum class ExitReason(val displayName: String) {
    MEAN_REVERSION("Mean Reversion"),
    STOP_LOSS("Stop Loss"),
    EXPIRY("Contract Expiry"),
    END_OF_PERIOD("End of Test Period")
}

/**
 * Settings for backtest and analytics configuration.
 */
data class Settings(
    val zScoreEntryThreshold: Double = 2.0,
    val zScoreExitThreshold: Double = 0.5,
    val rollingWindow: Int = 20,         // Trading days for rolling statistics
    val minDataPoints: Int = 15,         // Minimum observations before computing z-score
    val minDailyVolume: Long = 10,       // Minimum average daily volume
    val minOpenInterest: Long = 50,      // Minimum open interest
    val slippageBps: Double = 5.0,       // 5 basis points slippage
    val brokeragePerLot: Double = 20.0,  // INR 20 per lot per side
    val exchangeFeePercent: Double = 0.0026, // 0.0026%
    val gstPercent: Double = 18.0,       // 18% GST on brokerage + exchange fees
    val initialCapital: Double = 1_000_000.0, // INR 10 lakh
    val maxPositionSize: Int = 5         // Maximum lots per leg
)
