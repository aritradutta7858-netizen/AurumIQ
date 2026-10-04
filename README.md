# AurumIQ — Commodity Derivatives Intelligence

> **Hack in Hills 2026** — Quantitative Research Product for MCX Gold Futures Analysis
> 
> 🌐 **Live Web Demo:** [https://aritradutta7858-netizen.github.io/AurumIQ/](https://aritradutta7858-netizen.github.io/AurumIQ/)

---

## What Is This?

AurumIQ is an analytical research tool that normalizes, compares, and analyzes MCX gold futures contracts (GOLDM, GOLDTEN, GOLDGUINEA, GOLDPETAL) to detect statistically unusual relative price deviations.

**This is NOT a trading platform.** It is a genuine analytical research product where every number shown originates from the loaded dataset or an explicitly documented assumption.

---

## The Problem

MCX lists multiple gold futures contracts representing the same underlying metal, but with different:
- **Lot sizes**: 1g to 100g
- **Quotation bases**: per 1g, 8g, or 10g
- **Purities**: 995 or 999 parts per thousand

Raw price comparison across these contracts is **meaningless** without normalization.

---

## How It Works

### 1. Normalization
All prices are converted to a **common basis: INR per 10g of 999-purity gold**.

```
P_normalized = P_raw × (10 / quotation_grams) × (999 / purity)
```

### 2. Relative Spread
```
Spread% = (Price_A - Price_B) / Price_B × 100
```

### 3. Z-Score (Statistical Significance)
```
z = (current_spread - rolling_mean) / rolling_std_dev
```
Default threshold: ±2.0

### 4. Liquidity Filter
Signals on illiquid contracts are flagged with warnings.

### 5. Transaction Cost Estimation
Every signal is evaluated against realistic costs (slippage, brokerage, exchange fees, GST).

### 6. Walk-Forward Backtesting
No look-ahead bias. Training/test split with chronological signal generation.

---

## Technology Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM |
| Database | Room |
| Concurrency | Coroutines + StateFlow |
| Navigation | Navigation Compose |

---

## Contract Specifications

| Contract | Lot Size | Quotation | Purity |
|----------|----------|-----------|--------|
| GOLDM | 100g | per 10g | 995 |
| GOLDTEN | 10g | per 10g | 999 |
| GOLDGUINEA | 8g | per 8g | 999 |
| GOLDPETAL | 1g | per 1g | 999 |

---

## Signal Categories

| Category | Meaning |
|----------|---------|
| NORMAL | Spread within normal range |
| WATCH | Approaching threshold |
| HIGH_RELATIVE_PREMIUM | Statistically unusual premium (z > +threshold) |
| HIGH_RELATIVE_DISCOUNT | Statistically unusual discount (z < -threshold) |
| LIQUIDITY_WARNING | Signal detected but low liquidity |
| INSUFFICIENT_DATA | Not enough history for reliable statistics |

---

## Demo Path (< 3 minutes)

1. **Dashboard** → Overview with active contracts, latest signals, data quality
2. **Relative Value** → All pair signals sorted by z-score
3. **Select GOLDM vs GOLDTEN** → Pair analysis
4. **Normalized Prices** → See the common-basis price comparison
5. **Unusual Spread** → Z-score chart showing deviations
6. **Liquidity** → Volume and open interest analysis
7. **Transaction Costs** → Full cost breakdown
8. **Backtest** → Walk-forward results with equity curve
9. **Methodology** → Educational explanation

---

## Data Labels

Every data point is labeled:

| Label | Meaning |
|-------|---------|
| DATA | From the loaded dataset |
| CALCULATED | Derived from data using documented formulas |
| ASSUMPTION | Configurable parameter (e.g., slippage) |
| BACKTEST | Result of historical simulation |
| DEMO | Synthetic data — NOT real MCX data |

---

## Building

```bash
# Requires Android Studio or command-line SDK
./gradlew assembleDebug

# Run unit tests
./gradlew test
```

---

## Disclaimer

⚠️ **This tool uses SYNTHETIC DEMO DATA for demonstration purposes.**

It does NOT:
- Use live MCX data
- Guarantee profits or arbitrage
- Execute real trades
- Provide investment advice

All backtest results are based on simulated data and do not represent actual trading performance.

---

## Team

Built for **Hack in Hills 2026** | AurumIQ Team
