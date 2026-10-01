package com.aurumiq.app.data.local

import androidx.room.*
import com.aurumiq.app.data.model.MarketRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface MarketRecordDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(records: List<MarketRecord>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: MarketRecord): Long

    @Query("SELECT * FROM market_records WHERE symbol = :symbol ORDER BY date ASC")
    fun getBySymbol(symbol: String): Flow<List<MarketRecord>>

    @Query("SELECT * FROM market_records WHERE symbol = :symbol AND expiryDate = :expiryDate ORDER BY date ASC")
    fun getBySymbolAndExpiry(symbol: String, expiryDate: Long): Flow<List<MarketRecord>>

    @Query("SELECT * FROM market_records WHERE date = :date ORDER BY symbol ASC")
    suspend fun getByDate(date: Long): List<MarketRecord>

    @Query("SELECT * FROM market_records WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC, symbol ASC")
    suspend fun getByDateRange(startDate: Long, endDate: Long): List<MarketRecord>

    @Query("SELECT * FROM market_records WHERE symbol = :symbol AND date BETWEEN :startDate AND :endDate AND expiryDate >= :date ORDER BY date ASC")
    suspend fun getActiveBySymbolAndDateRange(symbol: String, startDate: Long, endDate: Long, date: Long): List<MarketRecord>

    @Query("SELECT DISTINCT symbol FROM market_records ORDER BY symbol")
    fun getDistinctSymbols(): Flow<List<String>>

    @Query("SELECT DISTINCT expiryDate FROM market_records WHERE symbol = :symbol ORDER BY expiryDate")
    suspend fun getExpiryDatesForSymbol(symbol: String): List<Long>

    @Query("SELECT DISTINCT date FROM market_records ORDER BY date ASC")
    suspend fun getAllTradingDates(): List<Long>

    @Query("SELECT MIN(date) FROM market_records")
    suspend fun getMinDate(): Long?

    @Query("SELECT MAX(date) FROM market_records")
    suspend fun getMaxDate(): Long?

    @Query("SELECT COUNT(*) FROM market_records")
    fun getRecordCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT symbol) FROM market_records")
    suspend fun getSymbolCount(): Int

    @Query("SELECT * FROM market_records WHERE symbol = :symbol AND date <= :date AND expiryDate >= :date ORDER BY date DESC LIMIT 1")
    suspend fun getLatestActiveRecord(symbol: String, date: Long): MarketRecord?

    @Query("""
        SELECT * FROM market_records 
        WHERE symbol IN (:symbolA, :symbolB) 
        AND date BETWEEN :startDate AND :endDate 
        ORDER BY date ASC, symbol ASC
    """)
    suspend fun getPairData(symbolA: String, symbolB: String, startDate: Long, endDate: Long): List<MarketRecord>

    @Query("DELETE FROM market_records")
    suspend fun deleteAll()

    @Query("DELETE FROM market_records WHERE dataSource = 'DEMO'")
    suspend fun deleteDemoData()
}
