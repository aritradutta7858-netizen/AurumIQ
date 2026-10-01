package com.aurumiq.app.data.model

/**
 * A price that has been normalized to a common basis for cross-contract comparison.
 *
 * Common basis: INR per 10g of 999-purity gold.
 *
 * Normalization formula:
 *   normalizedPrice = rawPrice × (quotationBasisGrams / 10) × (999 / purity)
 *
 * Step 1 — Quotation adjustment:
 *   Convert from "per quotationBasisGrams" to "per 10g"
 *   Factor = quotationBasisGrams / 10
 *   If quoting per 8g, multiply by 8/10 = 0.8 to get per-gram then ×10
 *   Actually: rawPrice / quotationBasisGrams × 10 = rawPrice × (10 / quotationBasisGrams)
 *
 * Step 2 — Purity adjustment:
 *   Convert from the contract's purity to 999 purity
 *   Factor = 999 / purity
 *   For 995: multiply by 999/995 ≈ 1.00402
 *   For 999: multiply by 1.0 (no adjustment)
 *
 * Combined formula:
 *   normalizedPrice = rawPrice × (10 / quotationBasisGrams) × (999 / purity)
 */
data class NormalizedPrice(
    val symbol: String,
    val date: Long,
    val expiryDate: Long,
    val rawPrice: Double,          // Original price from market data
    val normalizedPrice: Double,   // INR per 10g of 999-purity gold
    val quotationAdjustment: Double, // Multiplier for quotation basis conversion
    val purityAdjustment: Double,    // Multiplier for purity conversion
    val volume: Long,
    val openInterest: Long,
    val dataSource: DataSource
)
