package com.aurumiq.app.data.repository

import com.aurumiq.app.data.demo.DemoDataGenerator
import com.aurumiq.app.data.local.MarketRecordDao
import com.aurumiq.app.data.model.*
import com.aurumiq.app.data.parser.CsvParser
import com.aurumiq.app.engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Central repository for all data operations.
 * Mediates between the data layer (Room, CSV) and the engine layer.
 */
class MarketRepository(private val dao: MarketRecordDao) {

    // ── Data Access ──────────────────────────────────────────

    fun getRecordCount(): Flow<Int> = dao.getRecordCount()

    fun getDistinctSymbols(): Flow<List<String>> = dao.getDistinctSymbols()

    fun getBySymbol(symbol: String): Flow<List<MarketRecord>> = dao.getBySymbol(symbol)

    suspend fun getByDateRange(startDate: Long, endDate: Long): List<MarketRecord> =
        dao.getByDateRange(startDate, endDate)

    suspend fun getDateRange(): Pair<Long, Long>? {
        val min = dao.getMinDate() ?: return null
        val max = dao.getMaxDate() ?: return null
        return Pair(min, max)
    }

    suspend fun getAllTradingDates(): List<Long> = dao.getAllTradingDates()

    suspend fun getExpiryDatesForSymbol(symbol: String): List<Long> =
        dao.getExpiryDatesForSymbol(symbol)

    // ── Data Import ──────────────────────────────────────────

    suspend fun importCsv(inputStream: InputStream): CsvParser.ParseResult =
        withContext(Dispatchers.IO) {
            val parser = CsvParser()
            val result = parser.parse(inputStream)
            if (result.records.isNotEmpty()) {
                dao.insertAll(result.records)
            }
            result
        }

    suspend fun loadDemoData(): Int = withContext(Dispatchers.IO) {
        dao.deleteDemoData() // Clear old demo data
        val records = DemoDataGenerator.generate(500)
        val inserted = dao.insertAll(records)
        inserted.count { it != -1L }
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        dao.deleteAll()
    }

    // ── Analytics ─────────────────────────────────────────────

    suspend fun getPairData(
        symbolA: String,
        symbolB: String,
        startDate: Long? = null,
        endDate: Long? = null
    ): Pair<List<MarketRecord>, List<MarketRecord>> = withContext(Dispatchers.IO) {
        val range = getDateRange() ?: return@withContext Pair(emptyList(), emptyList())
        val start = startDate ?: range.first
        val end = endDate ?: range.second
        val allRecords = dao.getPairData(symbolA, symbolB, start, end)
        val recordsA = allRecords.filter { it.symbol == symbolA }.sortedBy { it.date }
        val recordsB = allRecords.filter { it.symbol == symbolB }.sortedBy { it.date }
        Pair(recordsA, recordsB)
    }

    suspend fun generateSignals(
        symbolA: String,
        symbolB: String,
        settings: Settings
    ): List<Signal> = withContext(Dispatchers.IO) {
        val (recordsA, recordsB) = getPairData(symbolA, symbolB)
        SignalEngine.generateSignals(recordsA, recordsB, settings)
    }

    suspend fun runBacktest(
        symbolA: String,
        symbolB: String,
        settings: Settings,
        trainingStart: Long,
        trainingEnd: Long,
        testStart: Long,
        testEnd: Long
    ): BacktestResult = withContext(Dispatchers.IO) {
        val (recordsA, recordsB) = getPairData(symbolA, symbolB, trainingStart, testEnd)
        BacktestEngine.runBacktest(
            recordsA, recordsB, settings,
            trainingStart, trainingEnd, testStart, testEnd
        )
    }

    // ── Dashboard Summary ─────────────────────────────────────

    data class DashboardSummary(
        val totalRecords: Int,
        val activeSymbols: List<String>,
        val dateRange: Pair<Long, Long>?,
        val latestSignals: List<Signal>,
        val dataQuality: DataQuality,
        val dataSource: DataSource
    )

    data class DataQuality(
        val totalRecords: Int,
        val symbolCount: Int,
        val dateRange: Pair<Long, Long>?,
        val avgRecordsPerDay: Double,
        val missingDaysEstimate: Int
    )

    suspend fun getDashboardSummary(settings: Settings): DashboardSummary =
        withContext(Dispatchers.IO) {
            val dateRange = getDateRange()
            val totalRecords = dao.getByDateRange(
                dateRange?.first ?: 0, dateRange?.second ?: Long.MAX_VALUE
            ).size
            val symbolCount = dao.getSymbolCount()
            val allDates = dao.getAllTradingDates()

            // Estimate missing days
            val expectedDays = if (dateRange != null) {
                ((dateRange.second - dateRange.first) / (24 * 60 * 60 * 1000) * 5 / 7).toInt()
            } else 0
            val missingDays = maxOf(0, expectedDays - allDates.size)

            val dataQuality = DataQuality(
                totalRecords = totalRecords,
                symbolCount = symbolCount,
                dateRange = dateRange,
                avgRecordsPerDay = if (allDates.isNotEmpty()) totalRecords.toDouble() / allDates.size else 0.0,
                missingDaysEstimate = missingDays
            )

            // Generate signals for the primary pair (GOLDM vs GOLDTEN)
            val latestSignals = try {
                val signals = generateSignals("GOLDM", "GOLDTEN", settings)
                signals.takeLast(30) // Last 30 signals
            } catch (e: Exception) {
                emptyList()
            }

            // Determine data source
            val sampleRecords = dao.getByDateRange(
                dateRange?.first ?: 0, dateRange?.second ?: Long.MAX_VALUE
            ).take(10)
            val dataSource = if (sampleRecords.all { it.dataSource == DataSource.DEMO }) {
                DataSource.DEMO
            } else if (sampleRecords.all { it.dataSource == DataSource.IMPORTED }) {
                DataSource.IMPORTED
            } else {
                DataSource.DEMO // Mixed defaults to demo label
            }

            DashboardSummary(
                totalRecords = totalRecords,
                activeSymbols = ContractSpec.ALL.map { it.symbol },
                dateRange = dateRange,
                latestSignals = latestSignals,
                dataQuality = dataQuality,
                dataSource = dataSource
            )
        }
}
