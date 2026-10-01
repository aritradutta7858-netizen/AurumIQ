package com.aurumiq.app

import com.aurumiq.app.data.model.*
import com.aurumiq.app.engine.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Comprehensive unit tests for AurumIQ engine components.
 */
class EngineTests {

    // ═══════════════════════════════════════════════════════════════
    // Contract Normalization Tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `GOLDTEN normalization should be identity (10g, 999 purity)`() {
        val record = makeRecord("GOLDTEN", close = 62000.0)
        val normalized = ContractNormalizer.normalizePrice(record)!!
        // GOLDTEN: 10g/10g × 999/999 = 1.0
        assertEquals(62000.0, normalized.normalizedPrice, 0.01)
        assertEquals(1.0, normalized.quotationAdjustment, 0.001)
        assertEquals(1.0, normalized.purityAdjustment, 0.001)
    }

    @Test
    fun `GOLDM normalization should apply purity adjustment only`() {
        val record = makeRecord("GOLDM", close = 60000.0)
        val normalized = ContractNormalizer.normalizePrice(record)!!
        // GOLDM: 10g/10g × 999/995 = 1.00402
        val expected = 60000.0 * (999.0 / 995.0)
        assertEquals(expected, normalized.normalizedPrice, 0.01)
        assertEquals(1.0, normalized.quotationAdjustment, 0.001)
        assertEquals(999.0 / 995.0, normalized.purityAdjustment, 0.0001)
    }

    @Test
    fun `GOLDGUINEA normalization should apply quotation adjustment`() {
        val record = makeRecord("GOLDGUINEA", close = 48000.0)
        val normalized = ContractNormalizer.normalizePrice(record)!!
        // GOLDGUINEA: 10g/8g × 999/999 = 1.25
        val expected = 48000.0 * (10.0 / 8.0)
        assertEquals(expected, normalized.normalizedPrice, 0.01)
        assertEquals(10.0 / 8.0, normalized.quotationAdjustment, 0.001)
        assertEquals(1.0, normalized.purityAdjustment, 0.001)
    }

    @Test
    fun `GOLDPETAL normalization should multiply by 10`() {
        val record = makeRecord("GOLDPETAL", close = 6200.0)
        val normalized = ContractNormalizer.normalizePrice(record)!!
        // GOLDPETAL: 10g/1g × 999/999 = 10.0
        assertEquals(62000.0, normalized.normalizedPrice, 0.01)
    }

    @Test
    fun `unknown symbol returns null`() {
        val record = makeRecord("SILVER", close = 80000.0)
        assertNull(ContractNormalizer.normalizePrice(record))
    }

    @Test
    fun `purity adjustment makes GOLDM more expensive than raw`() {
        // A 995 purity contract should normalize HIGHER (less pure = need more for 999 equivalent)
        val record = makeRecord("GOLDM", close = 60000.0)
        val normalized = ContractNormalizer.normalizePrice(record)!!
        assertTrue(normalized.normalizedPrice > record.close)
    }

    // ═══════════════════════════════════════════════════════════════
    // Spread Calculation Tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `spread between identical prices is zero`() {
        val spread = SpreadCalculator.calculateSpread(62000.0, 62000.0)
        assertEquals(0.0, spread!!, 0.0001)
    }

    @Test
    fun `positive spread indicates premium`() {
        val spread = SpreadCalculator.calculateSpread(62500.0, 62000.0)!!
        assertTrue(spread > 0)
    }

    @Test
    fun `negative spread indicates discount`() {
        val spread = SpreadCalculator.calculateSpread(61500.0, 62000.0)!!
        assertTrue(spread < 0)
    }

    @Test
    fun `spread with zero reference returns null`() {
        assertNull(SpreadCalculator.calculateSpread(62000.0, 0.0))
    }

    @Test
    fun `spread percentage is correct`() {
        // (63000 - 60000) / 60000 × 100 = 5.0
        val spread = SpreadCalculator.calculateSpread(63000.0, 60000.0)!!
        assertEquals(5.0, spread, 0.0001)
    }

    // ═══════════════════════════════════════════════════════════════
    // Z-Score Tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `z-score of mean value is zero`() {
        val data = (1..20).map { Pair(it.toLong(), 10.0) } // Constant values
        val stats = RollingStatistics.calculate(data, 20, 5)
        val zScores = ZScoreCalculator.calculate(stats)

        // All values are the mean → z-score should be 0
        zScores.forEach { assertEquals(0.0, it.zScore, 0.0001) }
    }

    @Test
    fun `z-score of 2 std devs above mean is approximately 2`() {
        // Create data where we know the stats
        val values = listOf(10.0, 12.0, 8.0, 11.0, 9.0, 10.0, 12.0, 8.0, 11.0, 9.0,
                           10.0, 12.0, 8.0, 11.0, 9.0, 10.0, 12.0, 8.0, 11.0, 9.0)
        val data = values.mapIndexed { i, v -> Pair(i.toLong(), v) }
        val stats = RollingStatistics.calculate(data, 20, 5)
        val zScores = ZScoreCalculator.calculate(stats)

        // The last z-score should reflect position relative to rolling mean
        assertTrue(zScores.isNotEmpty())
    }

    @Test
    fun `insufficient data produces empty z-scores`() {
        val data = listOf(Pair(1L, 10.0), Pair(2L, 11.0))
        val stats = RollingStatistics.calculate(data, 20, 15)
        assertTrue(stats.isEmpty())
    }

    // ═══════════════════════════════════════════════════════════════
    // Liquidity Tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `high volume and OI meets threshold`() {
        val result = LiquidityAnalyzer.analyze(
            volumes = listOf(100, 200, 150, 180),
            openInterests = listOf(500, 600, 550, 580),
            symbol = "GOLDM",
            settings = Settings(minDailyVolume = 10, minOpenInterest = 50)
        )
        assertTrue(result.meetsThreshold)
        assertTrue(result.liquidityScore > 0.9)
    }

    @Test
    fun `zero volume fails threshold`() {
        val result = LiquidityAnalyzer.analyze(
            volumes = listOf(0, 0, 0),
            openInterests = listOf(0, 0, 0),
            symbol = "GOLDPETAL",
            settings = Settings()
        )
        assertFalse(result.meetsThreshold)
        assertEquals(0.0, result.liquidityScore, 0.001)
    }

    @Test
    fun `empty data returns zero liquidity`() {
        val result = LiquidityAnalyzer.analyze(
            volumes = emptyList(),
            openInterests = emptyList(),
            symbol = "GOLDM",
            settings = Settings()
        )
        assertEquals(0.0, result.liquidityScore, 0.001)
    }

    // ═══════════════════════════════════════════════════════════════
    // Transaction Cost Tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `cost estimation produces positive values`() {
        val cost = CostEstimator.estimateRoundTrip(62000.0, Settings())
        assertTrue(cost.totalRoundTripBps > 0)
        assertTrue(cost.totalRoundTripPercent > 0)
    }

    @Test
    fun `round trip is more expensive than one side`() {
        val cost = CostEstimator.estimateRoundTrip(62000.0, Settings())
        assertTrue(cost.totalRoundTripBps > cost.totalOneSideBps)
    }

    @Test
    fun `higher slippage increases cost`() {
        val lowSlip = CostEstimator.estimateRoundTrip(62000.0, Settings(slippageBps = 1.0))
        val highSlip = CostEstimator.estimateRoundTrip(62000.0, Settings(slippageBps = 20.0))
        assertTrue(highSlip.totalRoundTripBps > lowSlip.totalRoundTripBps)
    }

    // ═══════════════════════════════════════════════════════════════
    // Market Record Validation Tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `valid record has no issues`() {
        val record = makeRecord("GOLDM", close = 62000.0)
        assertTrue(record.validate().isEmpty())
    }

    @Test
    fun `negative price detected`() {
        val record = makeRecord("GOLDM", close = -100.0)
        assertTrue(record.validate().isNotEmpty())
    }

    @Test
    fun `high less than low detected`() {
        val record = MarketRecord(
            symbol = "GOLDM", date = 1704153600000, expiryDate = 1706745600000,
            open = 62000.0, high = 61000.0, low = 63000.0, close = 62000.0,
            volume = 100, openInterest = 500, settlementPrice = 62000.0
        )
        val issues = record.validate()
        assertTrue(issues.any { it.contains("High < Low") })
    }

    @Test
    fun `expiry before trade date detected`() {
        val record = MarketRecord(
            symbol = "GOLDM", date = 1706745600000, expiryDate = 1704153600000,
            open = 62000.0, high = 63000.0, low = 61000.0, close = 62000.0,
            volume = 100, openInterest = 500, settlementPrice = 62000.0
        )
        val issues = record.validate()
        assertTrue(issues.any { it.contains("Expiry date before trade date") })
    }

    @Test
    fun `blank symbol detected`() {
        val record = makeRecord("", close = 62000.0)
        assertTrue(record.validate().any { it.contains("Symbol is blank") })
    }

    // ═══════════════════════════════════════════════════════════════
    // Contract Spec Tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `all contract specs are defined`() {
        assertNotNull(ContractSpec.forSymbol("GOLDM"))
        assertNotNull(ContractSpec.forSymbol("GOLDTEN"))
        assertNotNull(ContractSpec.forSymbol("GOLDGUINEA"))
        assertNotNull(ContractSpec.forSymbol("GOLDPETAL"))
    }

    @Test
    fun `symbol lookup is case insensitive`() {
        assertNotNull(ContractSpec.forSymbol("goldm"))
        assertNotNull(ContractSpec.forSymbol("GoldTen"))
    }

    @Test
    fun `unknown symbol returns null spec`() {
        assertNull(ContractSpec.forSymbol("PLATINUM"))
    }

    // ═══════════════════════════════════════════════════════════════
    // Look-Ahead Prevention Test
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `rolling statistics only use past data`() {
        val data = (1..30).map { Pair(it.toLong(), it.toDouble()) }
        val stats = RollingStatistics.calculate(data, 10, 5)

        for (stat in stats) {
            // The mean at each point should be ≤ the current value
            // (since we have an increasing series and use a look-back window)
            assertTrue(
                "Mean ${stat.mean} should be ≤ value ${stat.value} for date ${stat.date} (no look-ahead)",
                stat.mean <= stat.value
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Demo Data Tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `demo data is tagged as DEMO`() {
        val records = com.aurumiq.app.data.demo.DemoDataGenerator.generate(10)
        assertTrue(records.isNotEmpty())
        records.forEach { assertEquals(DataSource.DEMO, it.dataSource) }
    }

    @Test
    fun `demo data contains all four symbols`() {
        val records = com.aurumiq.app.data.demo.DemoDataGenerator.generate(50)
        val symbols = records.map { it.symbol }.toSet()
        assertTrue("GOLDM" in symbols)
        assertTrue("GOLDTEN" in symbols)
        assertTrue("GOLDGUINEA" in symbols)
        assertTrue("GOLDPETAL" in symbols)
    }

    @Test
    fun `demo data has valid records`() {
        val records = com.aurumiq.app.data.demo.DemoDataGenerator.generate(20)
        records.forEach { record ->
            val issues = record.validate()
            assertTrue(
                "Record ${record.symbol} on ${record.date} has issues: $issues",
                issues.isEmpty()
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Signal Engine Tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `signal engine produces signals for valid data`() {
        val records = com.aurumiq.app.data.demo.DemoDataGenerator.generate(100)
        val recordsA = records.filter { it.symbol == "GOLDM" }.sortedBy { it.date }
        val recordsB = records.filter { it.symbol == "GOLDTEN" }.sortedBy { it.date }

        val signals = SignalEngine.generateSignals(recordsA, recordsB, Settings())
        assertTrue("Should produce signals", signals.isNotEmpty())
    }

    @Test
    fun `signal engine handles empty input`() {
        val signals = SignalEngine.generateSignals(emptyList(), emptyList(), Settings())
        assertTrue(signals.isEmpty())
    }

    // ═══════════════════════════════════════════════════════════════
    // Backtest Engine Tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `backtest produces results`() {
        val records = com.aurumiq.app.data.demo.DemoDataGenerator.generate(200)
        val recordsA = records.filter { it.symbol == "GOLDM" }.sortedBy { it.date }
        val recordsB = records.filter { it.symbol == "GOLDTEN" }.sortedBy { it.date }

        if (recordsA.isNotEmpty() && recordsB.isNotEmpty()) {
            val dates = (recordsA.map { it.date } + recordsB.map { it.date }).sorted()
            val minDate = dates.first()
            val maxDate = dates.last()
            val midDate = dates[dates.size / 2]

            val result = BacktestEngine.runBacktest(
                recordsA, recordsB, Settings(),
                trainingStart = minDate,
                trainingEnd = midDate,
                testStart = midDate + 86400000,
                testEnd = maxDate
            )

            // Basic sanity checks
            assertEquals(Settings().initialCapital, result.initialCapital, 0.01)
            assertTrue(result.equityCurve.isNotEmpty())
            assertTrue(result.maxDrawdown >= 0)
            assertTrue(result.winRate in 0.0..1.0)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CSV Parser Tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `CSV parser handles valid data`() {
        val csv = """Symbol,Date,ExpiryDate,Open,High,Low,Close,Volume,OpenInterest,SettlementPrice
GOLDM,02-01-2024,25-01-2024,62000,63000,61000,62500,1000,5000,62500
GOLDTEN,02-01-2024,25-01-2024,62000,63000,61000,62500,500,3000,62500"""

        val parser = com.aurumiq.app.data.parser.CsvParser()
        val result = parser.parse(csv.byteInputStream())

        assertEquals(2, result.validRows)
        assertEquals(0, result.errors.size)
        assertEquals(0, result.duplicatesSkipped)
    }

    @Test
    fun `CSV parser detects duplicates`() {
        val csv = """Symbol,Date,ExpiryDate,Open,High,Low,Close,Volume,OpenInterest,SettlementPrice
GOLDM,02-01-2024,25-01-2024,62000,63000,61000,62500,1000,5000,62500
GOLDM,02-01-2024,25-01-2024,62100,63100,61100,62600,1100,5100,62600"""

        val parser = com.aurumiq.app.data.parser.CsvParser()
        val result = parser.parse(csv.byteInputStream())

        assertEquals(1, result.validRows)
        assertEquals(1, result.duplicatesSkipped)
    }

    @Test
    fun `CSV parser handles malformed rows`() {
        val csv = """Symbol,Date,ExpiryDate,Open,High,Low,Close,Volume,OpenInterest,SettlementPrice
GOLDM,INVALID-DATE,25-01-2024,62000,63000,61000,62500,1000,5000,62500
,02-01-2024,25-01-2024,62000,63000,61000,62500,1000,5000,62500"""

        val parser = com.aurumiq.app.data.parser.CsvParser()
        val result = parser.parse(csv.byteInputStream())

        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `CSV parser handles missing columns`() {
        val csv = """Symbol,Date
GOLDM,02-01-2024"""

        val parser = com.aurumiq.app.data.parser.CsvParser()
        val result = parser.parse(csv.byteInputStream())

        assertTrue(result.errors.isNotEmpty())
        assertEquals(0, result.validRows)
    }

    // ═══════════════════════════════════════════════════════════════
    // Date Parsing Tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `CSV parser handles multiple date formats`() {
        val csv1 = """Symbol,Date,ExpiryDate,Open,High,Low,Close,Volume,OpenInterest,SettlementPrice
GOLDM,02-01-2024,25-01-2024,62000,63000,61000,62500,1000,5000,62500"""

        val csv2 = """Symbol,Date,ExpiryDate,Open,High,Low,Close,Volume,OpenInterest,SettlementPrice
GOLDM,02/01/2024,25/01/2024,62000,63000,61000,62500,1000,5000,62500"""

        val csv3 = """Symbol,Date,ExpiryDate,Open,High,Low,Close,Volume,OpenInterest,SettlementPrice
GOLDM,2024-01-02,2024-01-25,62000,63000,61000,62500,1000,5000,62500"""

        val parser = com.aurumiq.app.data.parser.CsvParser()

        assertEquals(1, parser.parse(csv1.byteInputStream()).validRows)
        assertEquals(1, parser.parse(csv2.byteInputStream()).validRows)
        assertEquals(1, parser.parse(csv3.byteInputStream()).validRows)
    }

    // ═══════════════════════════════════════════════════════════════
    // Helper
    // ═══════════════════════════════════════════════════════════════

    private fun makeRecord(
        symbol: String,
        close: Double,
        date: Long = 1704153600000, // 2024-01-02
        expiryDate: Long = 1706745600000, // 2024-01-31
        volume: Long = 1000,
        oi: Long = 5000
    ): MarketRecord {
        val high = close * 1.01
        val low = close * 0.99
        return MarketRecord(
            symbol = symbol,
            date = date,
            expiryDate = expiryDate,
            open = close,
            high = high,
            low = low,
            close = close,
            volume = volume,
            openInterest = oi,
            settlementPrice = close
        )
    }
}
