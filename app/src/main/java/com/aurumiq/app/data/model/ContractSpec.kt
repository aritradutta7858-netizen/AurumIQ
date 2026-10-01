package com.aurumiq.app.data.model

/**
 * Explicit contract specification for an MCX gold futures contract.
 *
 * Each gold contract type on MCX has a different:
 *   - Lot size (grams per contract)
 *   - Quotation basis (price quoted per N grams)
 *   - Purity (fineness in parts per thousand)
 *
 * These differences make raw price comparison meaningless.
 * ContractSpec captures these parameters so that prices can be
 * normalized to a common basis for valid comparison.
 */
data class ContractSpec(
    val symbol: String,
    val displayName: String,
    val lotSizeGrams: Double,       // Total grams per contract
    val quotationBasisGrams: Double, // Price is quoted per this many grams
    val purityPartsPerThousand: Int  // e.g., 995 = 99.5%, 999 = 99.9%
) {
    companion object {
        /**
         * MCX Gold contract specifications.
         * Source: MCX contract specification documents.
         *
         * GOLDM:      100g lot, quoted per 10g, 995 purity
         * GOLDTEN:     10g lot, quoted per 10g, 999 purity
         * GOLDGUINEA:   8g lot, quoted per  8g, 999 purity
         * GOLDPETAL:    1g lot, quoted per  1g, 999 purity
         */
        val GOLDM = ContractSpec(
            symbol = "GOLDM",
            displayName = "Gold Mini",
            lotSizeGrams = 100.0,
            quotationBasisGrams = 10.0,
            purityPartsPerThousand = 995
        )

        val GOLDTEN = ContractSpec(
            symbol = "GOLDTEN",
            displayName = "Gold Ten",
            lotSizeGrams = 10.0,
            quotationBasisGrams = 10.0,
            purityPartsPerThousand = 999
        )

        val GOLDGUINEA = ContractSpec(
            symbol = "GOLDGUINEA",
            displayName = "Gold Guinea",
            lotSizeGrams = 8.0,
            quotationBasisGrams = 8.0,
            purityPartsPerThousand = 999
        )

        val GOLDPETAL = ContractSpec(
            symbol = "GOLDPETAL",
            displayName = "Gold Petal",
            lotSizeGrams = 1.0,
            quotationBasisGrams = 1.0,
            purityPartsPerThousand = 999
        )

        val ALL = listOf(GOLDM, GOLDTEN, GOLDGUINEA, GOLDPETAL)

        fun forSymbol(symbol: String): ContractSpec? =
            ALL.find { it.symbol.equals(symbol, ignoreCase = true) }
    }
}
