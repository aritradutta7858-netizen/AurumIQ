package com.aurumiq.app.engine

import com.aurumiq.app.data.model.Settings

/**
 * Estimates transaction costs for a round-trip trade.
 *
 * Cost components:
 *   1. Slippage: Market impact when entering/exiting
 *   2. Brokerage: Broker commission per lot per side
 *   3. Exchange fees: MCX transaction charges
 *   4. GST: On brokerage + exchange fees
 *
 * All costs are expressed in basis points (bps) for comparability with spread.
 * 1 basis point = 0.01%
 *
 * Total round-trip cost = 2 × (slippage + brokerage_bps + exchange_fee + gst_on_fees)
 * Factor of 2 because a spread trade has 2 legs.
 */
object CostEstimator {

    data class CostBreakdown(
        val slippageBps: Double,
        val brokerageBps: Double,
        val exchangeFeeBps: Double,
        val gstBps: Double,
        val totalOneSideBps: Double,
        val totalRoundTripBps: Double,
        val totalRoundTripPercent: Double
    )

    /**
     * Estimate round-trip cost for a spread trade (both legs).
     *
     * @param pricePerUnit Price per unit for brokerage calculation
     * @param settings Cost configuration
     * @return CostBreakdown with all components
     */
    fun estimateRoundTrip(pricePerUnit: Double, settings: Settings): CostBreakdown {
        // Slippage: configured in bps, applied both ways
        val slippageBps = settings.slippageBps

        // Brokerage: convert fixed amount to bps
        // brokerageBps = (brokerage / pricePerUnit) × 10000
        val brokerageBps = if (pricePerUnit > 0) {
            (settings.brokeragePerLot / pricePerUnit) * 10000.0
        } else 0.0

        // Exchange fee: already in percent, convert to bps
        val exchangeFeeBps = settings.exchangeFeePercent * 100.0

        // GST on (brokerage + exchange fee)
        val gstBps = (brokerageBps + exchangeFeeBps) * (settings.gstPercent / 100.0)

        val oneSide = slippageBps + brokerageBps + exchangeFeeBps + gstBps

        // Round-trip: both legs, 2 sides each
        // Spread trade = 4 transactions (buy A, sell B, then sell A, buy B)
        val roundTrip = oneSide * 4.0

        return CostBreakdown(
            slippageBps = slippageBps,
            brokerageBps = brokerageBps,
            exchangeFeeBps = exchangeFeeBps,
            gstBps = gstBps,
            totalOneSideBps = oneSide,
            totalRoundTripBps = roundTrip,
            totalRoundTripPercent = roundTrip / 100.0
        )
    }
}
