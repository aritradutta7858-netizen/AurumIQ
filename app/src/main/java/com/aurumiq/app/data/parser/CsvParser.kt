package com.aurumiq.app.data.parser

import com.aurumiq.app.data.model.DataSource
import com.aurumiq.app.data.model.MarketRecord
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Parses MCX-style CSV files into MarketRecord objects.
 *
 * Expected CSV format (header row required):
 *   Symbol, Date, ExpiryDate, Open, High, Low, Close, Volume, OpenInterest, SettlementPrice
 *
 * Supports date formats: dd-MM-yyyy, dd/MM/yyyy, yyyy-MM-dd
 */
class CsvParser {

    data class ParseResult(
        val records: List<MarketRecord>,
        val errors: List<ParseError>,
        val duplicatesSkipped: Int,
        val totalRows: Int,
        val validRows: Int
    )

    data class ParseError(
        val lineNumber: Int,
        val line: String,
        val error: String
    )

    private val dateFormats = listOf(
        SimpleDateFormat("dd-MM-yyyy", Locale.US),
        SimpleDateFormat("dd/MM/yyyy", Locale.US),
        SimpleDateFormat("yyyy-MM-dd", Locale.US),
        SimpleDateFormat("dd-MMM-yyyy", Locale.US)
    ).onEach { it.timeZone = TimeZone.getTimeZone("UTC"); it.isLenient = false }

    fun parse(inputStream: InputStream): ParseResult {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val records = mutableListOf<MarketRecord>()
        val errors = mutableListOf<ParseError>()
        val seen = mutableSetOf<String>()
        var duplicatesSkipped = 0
        var lineNumber = 0

        // Read header
        val header = reader.readLine() ?: return ParseResult(emptyList(), emptyList(), 0, 0, 0)
        lineNumber++

        // Map header columns (case-insensitive, trimmed)
        val columns = header.split(",").map { it.trim().lowercase() }
        val colMap = mapColumnsToIndices(columns)

        if (colMap == null) {
            errors.add(ParseError(1, header, "Missing required columns. Expected: Symbol, Date, ExpiryDate, Open, High, Low, Close, Volume, OpenInterest, SettlementPrice"))
            return ParseResult(emptyList(), errors, 0, 1, 0)
        }

        reader.forEachLine { line ->
            lineNumber++
            if (line.isBlank()) return@forEachLine

            try {
                val parts = line.split(",").map { it.trim() }
                if (parts.size < colMap.minColumns) {
                    errors.add(ParseError(lineNumber, line, "Insufficient columns: ${parts.size} < ${colMap.minColumns}"))
                    return@forEachLine
                }

                val symbol = parts[colMap.symbol].uppercase()
                val date = parseDate(parts[colMap.date])
                val expiryDate = parseDate(parts[colMap.expiryDate])
                val open = parts[colMap.open].toDoubleOrNull()
                val high = parts[colMap.high].toDoubleOrNull()
                val low = parts[colMap.low].toDoubleOrNull()
                val close = parts[colMap.close].toDoubleOrNull()
                val volume = parts[colMap.volume].toLongOrNull()
                val oi = parts[colMap.openInterest].toLongOrNull()
                val settlement = parts[colMap.settlementPrice].toDoubleOrNull()

                if (date == null) {
                    errors.add(ParseError(lineNumber, line, "Cannot parse date: ${parts[colMap.date]}"))
                    return@forEachLine
                }
                if (expiryDate == null) {
                    errors.add(ParseError(lineNumber, line, "Cannot parse expiry date: ${parts[colMap.expiryDate]}"))
                    return@forEachLine
                }

                val record = MarketRecord(
                    symbol = symbol,
                    date = date,
                    expiryDate = expiryDate,
                    open = open ?: 0.0,
                    high = high ?: 0.0,
                    low = low ?: 0.0,
                    close = close ?: 0.0,
                    volume = volume ?: 0,
                    openInterest = oi ?: 0,
                    settlementPrice = settlement ?: (close ?: 0.0),
                    dataSource = DataSource.IMPORTED
                )

                // Validate
                val issues = record.validate()
                if (issues.isNotEmpty()) {
                    errors.add(ParseError(lineNumber, line, "Validation: ${issues.joinToString("; ")}"))
                    return@forEachLine
                }

                // Duplicate detection
                val key = "${record.symbol}|${record.date}|${record.expiryDate}"
                if (key in seen) {
                    duplicatesSkipped++
                    return@forEachLine
                }
                seen.add(key)

                records.add(record)
            } catch (e: Exception) {
                errors.add(ParseError(lineNumber, line, "Exception: ${e.message}"))
            }
        }

        return ParseResult(
            records = records,
            errors = errors,
            duplicatesSkipped = duplicatesSkipped,
            totalRows = lineNumber - 1, // Exclude header
            validRows = records.size
        )
    }

    private fun parseDate(text: String): Long? {
        val trimmed = text.trim()
        for (fmt in dateFormats) {
            try {
                val date = fmt.parse(trimmed) ?: continue
                return date.time
            } catch (_: Exception) { }
        }
        return null
    }

    private data class ColumnMap(
        val symbol: Int,
        val date: Int,
        val expiryDate: Int,
        val open: Int,
        val high: Int,
        val low: Int,
        val close: Int,
        val volume: Int,
        val openInterest: Int,
        val settlementPrice: Int,
        val minColumns: Int
    )

    private fun mapColumnsToIndices(columns: List<String>): ColumnMap? {
        fun find(vararg names: String): Int? =
            names.firstNotNullOfOrNull { name -> columns.indexOfFirst { it == name }.takeIf { it >= 0 } }

        val symbol = find("symbol", "sym", "instrument") ?: return null
        val date = find("date", "tradedate", "trade_date", "tradingdate") ?: return null
        val expiryDate = find("expirydate", "expiry_date", "expiry", "exp_date") ?: return null
        val open = find("open", "openprice", "open_price") ?: return null
        val high = find("high", "highprice", "high_price") ?: return null
        val low = find("low", "lowprice", "low_price") ?: return null
        val close = find("close", "closeprice", "close_price", "ltp") ?: return null
        val volume = find("volume", "vol", "tradedqty", "traded_qty") ?: return null
        val oi = find("openinterest", "open_interest", "oi") ?: return null
        val settlement = find("settlementprice", "settlement_price", "settlement", "settle") ?: return null

        val maxIdx = maxOf(symbol, date, expiryDate, open, high, low, close, volume, oi, settlement)

        return ColumnMap(symbol, date, expiryDate, open, high, low, close, volume, oi, settlement, maxIdx + 1)
    }
}
