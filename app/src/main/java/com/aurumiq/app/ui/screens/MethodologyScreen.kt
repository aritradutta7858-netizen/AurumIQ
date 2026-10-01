package com.aurumiq.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aurumiq.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MethodologyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Methodology", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                        Text("How AurumIQ Works", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        containerColor = SurfaceDark
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                MethodologySection(
                    title = "1. The Problem: Why Normalization?",
                    content = """MCX lists multiple gold futures contracts:

• GOLDM — 100g lot, quoted per 10g, 995 purity
• GOLDTEN — 10g lot, quoted per 10g, 999 purity
• GOLDGUINEA — 8g lot, quoted per 8g, 999 purity
• GOLDPETAL — 1g lot, quoted per 1g, 999 purity

These contracts represent the SAME underlying metal but are quoted differently. A raw price of ₹62,000 for GOLDM and ₹6,200 for GOLDPETAL does NOT mean GOLDM is 10× more expensive.

Without normalization, comparing prices across these contracts is meaningless."""
                )
            }

            item {
                MethodologySection(
                    title = "2. Normalization Formula",
                    content = """We normalize all prices to a COMMON BASIS:

    INR per 10g of 999-purity gold

Formula:
    P_normalized = P_raw × (10 / Q) × (999 / purity)

Where:
    P_raw   = Quoted market price
    Q       = Quotation basis (grams)
    purity  = Contract purity (parts per 1000)

Step 1 — Quotation Adjustment:
    Convert from "per Q grams" to "per 10g"
    Factor = 10 / Q

    GOLDM (Q=10g):      × 1.000
    GOLDTEN (Q=10g):     × 1.000
    GOLDGUINEA (Q=8g):   × 1.250
    GOLDPETAL (Q=1g):    × 10.000

Step 2 — Purity Adjustment:
    Convert from contract purity to 999
    Factor = 999 / purity

    GOLDM (995):    × 1.00402
    GOLDTEN (999):  × 1.00000
    GOLDGUINEA:     × 1.00000
    GOLDPETAL:      × 1.00000

After normalization, all prices are directly comparable."""
                )
            }

            item {
                MethodologySection(
                    title = "3. Relative Spread",
                    content = """The spread measures the price difference between two normalized contracts as a percentage:

    Spread% = (Price_A - Price_B) / Price_B × 100

• Positive spread → A is at a PREMIUM to B
• Negative spread → A is at a DISCOUNT to B
• Zero spread → Contracts are priced identically

In efficient markets, normalized prices of contracts on the same metal should be nearly identical. Persistent deviations may indicate market microstructure effects, liquidity differences, or temporary mispricings."""
                )
            }

            item {
                MethodologySection(
                    title = "4. Z-Score: Statistical Significance",
                    content = """The z-score tells us how unusual the current spread is relative to recent history:

    z = (current_spread - rolling_mean) / rolling_std_dev

Interpretation:
    |z| < 1.0   → Normal variation
    |z| 1.0–2.0 → Elevated, worth watching
    |z| ≥ 2.0   → Statistically unusual (default signal threshold)
    |z| ≥ 3.0   → Highly unusual

The rolling window (default: 20 trading days) is configurable. A larger window provides more stable estimates but is slower to adapt.

CRITICAL: The z-score at date T uses ONLY data up to date T. No future information contaminates the calculation."""
                )
            }

            item {
                MethodologySection(
                    title = "5. Liquidity Analysis",
                    content = """Not all signals are tradeable. The liquidity analyzer evaluates:

• Average daily volume (vs. minimum threshold)
• Open interest (vs. minimum threshold)

Liquidity score = √(volume_ratio × OI_ratio)

Where each ratio is capped at 1.0 (meeting threshold = ratio of 1.0).

Signals on illiquid contracts are flagged with LIQUIDITY_WARNING. These may be statistically interesting but practically difficult to execute."""
                )
            }

            item {
                MethodologySection(
                    title = "6. Transaction Costs",
                    content = """Every signal is evaluated against realistic transaction costs:

Components:
• Slippage — Market impact (default: 5 bps per side)
• Brokerage — Broker commission (default: ₹20 per lot per side)
• Exchange fees — MCX transaction charges (default: 0.0026%)
• GST — 18% on brokerage + exchange fees

Round-trip cost = 4 × one-side cost
(Because a spread trade involves 4 transactions: buy A, sell B, then sell A, buy B)

Net spread = observed spread - round-trip cost

A signal that looks attractive but doesn't survive costs is not a genuine opportunity. All cost assumptions are EXPLICITLY LABELED."""
                )
            }

            item {
                MethodologySection(
                    title = "7. Contract Expiry Handling",
                    content = """Futures contracts expire. AurumIQ:

• Tracks expiry dates for each contract
• NEVER holds expired contracts in backtesting
• Forces position closure before expiry
• Reduces volume and OI expectations near expiry

Contract roll (switching to the next expiry) must be handled carefully to avoid artificial spread jumps. The system uses explicit expiry dates from the data, not estimated dates."""
                )
            }

            item {
                MethodologySection(
                    title = "8. Look-Ahead Bias Prevention",
                    content = """Look-ahead bias occurs when a model uses information that would not have been available at the time of the decision.

AurumIQ's strict rules:

✓ At date T, signals use ONLY data from dates ≤ T
✓ Rolling statistics window ends at T, never beyond
✓ Entry decision is made at T's close
✓ Execution is assumed at T+1's open
✓ No future expiry dates affect current decisions
✓ Training and test periods are strictly separated

Walk-forward structure:
    [Training: 60%] → [Test: 40%]

The training period builds the rolling window. The test period evaluates signals using only information available at each decision point."""
                )
            }

            item {
                MethodologySection(
                    title = "9. Walk-Forward Backtesting",
                    content = """Walk-forward testing is more realistic than simple backtesting:

1. Split data into TRAINING and TEST periods
2. Training period: establish rolling statistics baseline
3. Test period: generate and execute signals chronologically
4. Each test-period decision uses only past data

This prevents overfitting to the full dataset and provides a more honest evaluation of strategy performance.

IMPORTANT: The backtest results show performance on UNSEEN data (the test period). The model's parameters were not tuned to this data."""
                )
            }

            item {
                MethodologySection(
                    title = "10. Strategy vs Gold Movement",
                    content = """A gold spread strategy should be approximately market-neutral — its returns should not depend on gold's direction.

AurumIQ reports:
• Gold price return (during test period)
• Strategy return
• EXCESS return (strategy - gold)

If the excess return is similar to the total return, the strategy is genuinely capturing spread opportunities rather than just riding gold's direction.

If excess return is near zero or negative, the strategy may not add value beyond holding gold."""
                )
            }

            item {
                MethodologySection(
                    title = "11. Limitations & Disclaimers",
                    content = """AurumIQ is a RESEARCH and EDUCATIONAL tool.

Limitations:
• Synthetic demo data does not reflect real MCX dynamics
• Slippage estimates may understate actual market impact
• Model assumes continuous liquidity, which may not hold
• Transaction costs are estimates, not actuals
• Past backtest performance ≠ future results
• Spread convergence is not guaranteed
• Regulatory and margin requirements not modeled
• Model does not account for market holidays
• Position sizing is simplified

This tool does NOT:
• Guarantee profits or arbitrage
• Execute real trades
• Provide investment advice
• Replace professional financial analysis

Always validate with actual market data before drawing conclusions."""
                )
            }
        }
    }
}

@Composable
private fun MethodologySection(title: String, content: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceContainerDark
    ) {
        Column(Modifier.padding(16.dp), Arrangement.spacedBy(8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = GoldPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                content,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )
        }
    }
}
