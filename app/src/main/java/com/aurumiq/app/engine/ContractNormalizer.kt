package com.aurumiq.app.engine

import com.aurumiq.app.data.model.ContractSpec
import com.aurumiq.app.data.model.MarketRecord
import com.aurumiq.app.data.model.NormalizedPrice

/**
 * ContractNormalizer converts raw MCX futures prices to a common basis.
 *
 * ═══════════════════════════════════════════════════════════════
 * NORMALIZATION FORMULA
 * ═══════════════════════════════════════════════════════════════
 *
 * Target basis: INR per 10g of 999-purity gold.
 *
 * Given:
 *   P_raw  = raw quoted price
 *   Q      = quotation basis in grams (price is "per Q grams")
 *   purity = contract purity in parts per 1000
 *
 * Step 1 — Convert to "per 10g":
 *   P_10g = P_raw × (10 / Q)
 *
 *   Example — GOLDGUINEA (Q=8g):
 *     If quoted at ₹48,000 per 8g → ₹48,000 × (10/8) = ₹60,000 per 10g
 *
 *   Example — GOLDPETAL (Q=1g):
 *     If quoted at ₹6,100 per 1g → ₹6,100 × (10/1) = ₹61,000 per 10g
 *
 * Step 2 — Adjust purity to 999:
 *   P_normalized = P_10g × (999 / purity)
 *
 *   Example — GOLDM (purity=995):
 *     If P_10g = ₹60,000 → ₹60,000 × (999/995) = ₹60,241 per 10g @ 999
 *
 *   Example — GOLDTEN (purity=999):
 *     No adjustment needed: × (999/999) = ×1.0
 *
 * Combined:
 *   P_normalized = P_raw × (10 / Q) × (999 / purity)
 *
 * ═══════════════════════════════════════════════════════════════
 */
object ContractNormalizer {

    /**
     * Normalize a single market record's close price to common basis.
     *
     * @param record The raw market record
     * @return NormalizedPrice, or null if the symbol is not recognized
     */
    fun normalizePrice(record: MarketRecord): NormalizedPrice? {
        val spec = ContractSpec.forSymbol(record.symbol) ?: return null
        return normalizePrice(record, spec)
    }

    /**
     * Normalize using an explicit contract spec (for testing).
     */
    fun normalizePrice(record: MarketRecord, spec: ContractSpec): NormalizedPrice {
        val quotationAdj = 10.0 / spec.quotationBasisGrams
        val purityAdj = 999.0 / spec.purityPartsPerThousand.toDouble()

        val normalizedClose = record.close * quotationAdj * purityAdj

        return NormalizedPrice(
            symbol = record.symbol,
            date = record.date,
            expiryDate = record.expiryDate,
            rawPrice = record.close,
            normalizedPrice = normalizedClose,
            quotationAdjustment = quotationAdj,
            purityAdjustment = purityAdj,
            volume = record.volume,
            openInterest = record.openInterest,
            dataSource = record.dataSource
        )
    }

    /**
     * Normalize a batch of records.
     */
    fun normalizeBatch(records: List<MarketRecord>): List<NormalizedPrice> {
        return records.mapNotNull { normalizePrice(it) }
    }

    /**
     * Returns the combined adjustment factor for a given symbol.
     * Useful for display/debugging.
     */
    fun getAdjustmentFactor(symbol: String): Double? {
        val spec = ContractSpec.forSymbol(symbol) ?: return null
        return (10.0 / spec.quotationBasisGrams) * (999.0 / spec.purityPartsPerThousand.toDouble())
    }
}
