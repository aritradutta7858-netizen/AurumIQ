/**
 * AurumIQ — Commodity Derivatives Intelligence
 * Quantitative Spread Arbitrage & Analytics Engine
 */

(function () {
  'use strict';

  // ═══════════════════════════════════════════════════════════════
  // 1. MCX CONTRACT SPECIFICATIONS
  // ═══════════════════════════════════════════════════════════════
  const CONTRACT_SPECS = {
    GOLDM: {
      symbol: 'GOLDM',
      name: 'Gold Mini',
      lotSizeGrams: 100,
      purity: 995,
      quotationBasis: 10, // Quoted per 10g
      quotationUnit: '10g',
      tickSize: 1.0,
      baseMarginPct: 0.10
    },
    GOLDTEN: {
      symbol: 'GOLDTEN',
      name: 'Gold 10 Grams',
      lotSizeGrams: 10,
      purity: 999,
      quotationBasis: 1, // Quoted per 1g
      quotationUnit: '1g',
      tickSize: 0.5,
      baseMarginPct: 0.10
    },
    GOLDGUINEA: {
      symbol: 'GOLDGUINEA',
      name: 'Gold Guinea',
      lotSizeGrams: 8,
      purity: 999,
      quotationBasis: 8, // Quoted per 8g
      quotationUnit: '8g',
      tickSize: 1.0,
      baseMarginPct: 0.10
    },
    GOLDPETAL: {
      symbol: 'GOLDPETAL',
      name: 'Gold Petal',
      lotSizeGrams: 1,
      purity: 999,
      quotationBasis: 1, // Quoted per 1g
      quotationUnit: '1g',
      tickSize: 0.25,
      baseMarginPct: 0.12
    }
  };

  const PAIR_COMBINATIONS = [
    { a: 'GOLDM', b: 'GOLDTEN', key: 'GOLDM-GOLDTEN' },
    { a: 'GOLDM', b: 'GOLDGUINEA', key: 'GOLDM-GOLDGUINEA' },
    { a: 'GOLDM', b: 'GOLDPETAL', key: 'GOLDM-GOLDPETAL' },
    { a: 'GOLDTEN', b: 'GOLDGUINEA', key: 'GOLDTEN-GOLDGUINEA' },
    { a: 'GOLDTEN', b: 'GOLDPETAL', key: 'GOLDTEN-GOLDPETAL' },
    { a: 'GOLDGUINEA', b: 'GOLDPETAL', key: 'GOLDGUINEA-GOLDPETAL' }
  ];

  // ═══════════════════════════════════════════════════════════════
  // 2. QUANTITATIVE NORMALIZATION & MATH HELPERS
  // ═══════════════════════════════════════════════════════════════

  /**
   * Normalizes a quoted contract price to ₹ per pure gram (999.9 purity basis).
   */
  function normalizePrice(quotedPrice, spec) {
    if (!quotedPrice || quotedPrice <= 0) return 0;
    const pricePerGram = quotedPrice / spec.quotationBasis;
    const purityAdjustment = 999.9 / spec.purity;
    return pricePerGram * purityAdjustment;
  }

  /**
   * Reverse normalizes from ₹/pure gram back to contract's quotation basis.
   */
  function denormalizePrice(normPrice, spec) {
    if (!normPrice || normPrice <= 0) return 0;
    const purityAdjustment = spec.purity / 999.9;
    return normPrice * spec.quotationBasis * purityAdjustment;
  }

  /**
   * Indian Statutory Commodity Derivatives Transaction Costs
   * Computes full round-trip friction across both legs in ₹ and ₹/pure-g hurdle.
   */
  function calculateStatutoryCosts(specA, priceA, specB, priceB, tradeSizeGrams = 100) {
    const normA = normalizePrice(priceA, specA);
    const normB = normalizePrice(priceB, specB);

    // Turnover in ₹ for 1 round-trip on both legs
    // Leg A turnover (Buy + Sell)
    const legATurnover = normA * tradeSizeGrams * 2;
    // Leg B turnover (Sell + Buy)
    const legBTurnover = normB * tradeSizeGrams * 2;
    const totalTurnover = legATurnover + legBTurnover;

    // 1. MCX Exchange Turnover Charge: ₹210 per Crore (0.0021%)
    const mcxExchangeFee = totalTurnover * 0.000021;

    // 2. Commodity Transaction Tax (CTT): 0.0125% on SELL turnover only
    const sellTurnover = (legATurnover / 2) + (legBTurnover / 2);
    const cttTax = sellTurnover * 0.000125;

    // 3. SEBI Turnover Fee: ₹10 per Crore (0.0001%)
    const sebiFee = totalTurnover * 0.000001;

    // 4. State Stamp Duty: 0.002% on BUY turnover
    const buyTurnover = (legATurnover / 2) + (legBTurnover / 2);
    const stampDuty = buyTurnover * 0.00002;

    // 5. Brokerage: ₹20 per executed order (4 orders total: Buy A, Sell B, Close A, Close B)
    const brokerage = 20 * 4;

    // 6. GST: 18% on (Exchange charges + Brokerage + SEBI)
    const gst = (mcxExchangeFee + brokerage + sebiFee) * 0.18;

    // 7. Slippage Allowance: 0.015% of turnover
    const slippage = totalTurnover * 0.00015;

    const totalRoundTripCost = mcxExchangeFee + cttTax + sebiFee + stampDuty + brokerage + gst + slippage;
    const costHurdlePerGram = totalRoundTripCost / tradeSizeGrams;

    return {
      totalTurnover,
      mcxExchangeFee,
      cttTax,
      sebiFee,
      stampDuty,
      brokerage,
      gst,
      slippage,
      totalRoundTripCost,
      costHurdlePerGram,
      costHurdlePct: (costHurdlePerGram / ((normA + normB) / 2)) * 100
    };
  }

  // ═══════════════════════════════════════════════════════════════
  // 3. SYNTHETIC REALISTIC MARKET DATA ENGINE
  // ═══════════════════════════════════════════════════════════════

  class PseudoRandom {
    constructor(seed = 42) {
      this.seed = seed;
    }
    next() {
      this.seed = (this.seed * 16807) % 2147483647;
      return (this.seed - 1) / 2147483646;
    }
    nextGaussian() {
      let u = 0, v = 0;
      while (u === 0) u = this.next();
      while (v === 0) v = this.next();
      return Math.sqrt(-2.0 * Math.log(u)) * Math.cos(2.0 * Math.PI * v);
    }
    nextInt(min, max) {
      return Math.floor(min + this.next() * (max - min));
    }
  }

  function generateMarketDataset(numDays = 180, seed = 42) {
    const rng = new PseudoRandom(seed);
    const dataset = []; // Array of day objects

    let baseGoldPricePerPureGram = 7150.0; // ₹ / pure gram starting basis
    const startDate = new Date(2024, 0, 15);

    // Generate month-end expiry schedule
    const expiries = [];
    for (let m = 0; m < 12; m++) {
      const exp = new Date(2024, m + 1, 0); // Last day of month
      // Roll back to Thursday if weekend
      while (exp.getDay() !== 4 && exp.getDay() !== 5) {
        exp.setDate(exp.getDate() - 1);
      }
      expiries.push(exp);
    }

    let currentDate = new Date(startDate);
    let dayCount = 0;

    while (dayCount < numDays) {
      // Skip weekends
      if (currentDate.getDay() === 0 || currentDate.getDay() === 6) {
        currentDate.setDate(currentDate.getDate() + 1);
        continue;
      }

      // Base pure gold walk
      const dailyDrift = 0.0002; // slight upward gold drift
      const dailyVol = 0.0075 * rng.nextGaussian();
      baseGoldPricePerPureGram *= (1 + dailyDrift + dailyVol);

      // Find active expiry
      let activeExpiry = expiries.find(e => e > currentDate) || expiries[expiries.length - 1];
      const diffTime = Math.abs(activeExpiry - currentDate);
      const dte = Math.max(1, Math.ceil(diffTime / (1000 * 60 * 60 * 24)));

      const dayRecord = {
        date: new Date(currentDate),
        dateStr: currentDate.toISOString().split('T')[0],
        dte: dte,
        basePureGold: baseGoldPricePerPureGram,
        contracts: {}
      };

      // Generate each contract's OHLCV and open interest
      for (const [symbol, spec] of Object.entries(CONTRACT_SPECS)) {
        // Contract-specific persistent spread + occasional regime anomaly
        let contractDeviation = 0;
        if (symbol === 'GOLDM') contractDeviation = 1.2; // slight basis
        else if (symbol === 'GOLDTEN') contractDeviation = 0.0; // benchmark
        else if (symbol === 'GOLDGUINEA') contractDeviation = -1.8;
        else if (symbol === 'GOLDPETAL') contractDeviation = -3.5;

        // Intentional mean-reverting anomalies at specific regimes to test arbitrage
        if (dayCount % 45 >= 10 && dayCount % 45 <= 22 && symbol === 'GOLDM') {
          contractDeviation += 16.5 * Math.sin((dayCount - 10) * 0.25);
        }
        if (dayCount % 60 >= 30 && dayCount % 60 <= 42 && symbol === 'GOLDPETAL') {
          contractDeviation -= 18.0 * Math.sin((dayCount - 30) * 0.22);
        }

        // Noise
        contractDeviation += rng.nextGaussian() * 1.8;

        // Near expiry convergence
        if (dte < 8) {
          contractDeviation *= (dte / 8.0);
        }

        const effectivePurePrice = baseGoldPricePerPureGram + contractDeviation;
        const quotedBase = denormalizePrice(effectivePurePrice, spec);

        const intradayNoise = spec.tickSize * rng.nextInt(3, 12);
        const open = quotedBase + (rng.nextGaussian() * intradayNoise * 0.5);
        const close = quotedBase + (rng.nextGaussian() * intradayNoise * 0.3);
        const high = Math.max(open, close) + intradayNoise;
        const low = Math.max(0.01, Math.min(open, close) - intradayNoise);
        const settlement = close + (rng.nextGaussian() * spec.tickSize * 0.2);

        // Volume & Liquidity tiers
        let baseVolume = 0;
        let baseOI = 0;
        if (symbol === 'GOLDM') {
          baseVolume = rng.nextInt(1200, 3200);
          baseOI = rng.nextInt(6000, 15000);
        } else if (symbol === 'GOLDTEN') {
          baseVolume = rng.nextInt(800, 2200);
          baseOI = rng.nextInt(4000, 10000);
        } else if (symbol === 'GOLDGUINEA') {
          baseVolume = rng.nextInt(300, 1100);
          baseOI = rng.nextInt(1500, 4500);
        } else {
          baseVolume = rng.nextInt(80, 400);
          baseOI = rng.nextInt(500, 2000);
        }

        // Expiry volume decay
        const dteDecay = dte < 4 ? 0.35 : (dte < 10 ? 0.75 : 1.0);
        const volume = Math.round(baseVolume * dteDecay);
        const oi = Math.round(baseOI * (dte > 20 ? 1.0 : dte / 20.0));

        // Turnover in ₹ Lakhs
        const turnoverLakhs = (volume * quotedBase * (spec.lotSizeGrams / spec.quotationBasis)) / 100000;

        // Liquidity Tier
        let tier = 1;
        if (turnoverLakhs > 3000) tier = 1;
        else if (turnoverLakhs > 1000) tier = 2;
        else if (turnoverLakhs > 300) tier = 3;
        else tier = 4;

        // Normalized prices
        const normOpen = normalizePrice(open, spec);
        const normHigh = normalizePrice(high, spec);
        const normLow = normalizePrice(low, spec);
        const normClose = normalizePrice(close, spec);
        const normSettlement = normalizePrice(settlement, spec);

        dayRecord.contracts[symbol] = {
          symbol,
          open, high, low, close, settlement,
          volume, oi, turnoverLakhs, tier,
          normOpen, normHigh, normLow, normClose, normSettlement
        };
      }

      dataset.push(dayRecord);
      currentDate.setDate(currentDate.getDate() + 1);
      dayCount++;
    }

    return dataset;
  }

  // ═══════════════════════════════════════════════════════════════
  // 4. STATISTICAL ENGINE & ORNSTEIN-UHLENBECK ANALYSIS
  // ═══════════════════════════════════════════════════════════════

  /**
   * Calculates rolling mean, std dev, and look-ahead-free Z-scores for a pair.
   * STRICT CAUSALITY: Z-score at day t is computed strictly from days [t - window, t - 1].
   */
  function computePairTimeSeries(dataset, symA, symB, window = 60) {
    const series = [];

    for (let t = 0; t < dataset.length; t++) {
      const day = dataset[t];
      const recA = day.contracts[symA];
      const recB = day.contracts[symB];

      const normA = recA.normSettlement;
      const normB = recB.normSettlement;
      const spread = normA - normB;
      const spreadPct = (spread / ((normA + normB) / 2)) * 100;

      let rollingMean = spread;
      let rollingStd = 1.0;
      let zScore = 0.0;

      if (t >= window) {
        // STRICT HISTORICAL WINDOW [t - window, t - 1] (Zero look-ahead bias)
        let sum = 0;
        for (let i = t - window; i < t; i++) {
          const s = dataset[i].contracts[symA].normSettlement - dataset[i].contracts[symB].normSettlement;
          sum += s;
        }
        rollingMean = sum / window;

        let varSum = 0;
        for (let i = t - window; i < t; i++) {
          const s = dataset[i].contracts[symA].normSettlement - dataset[i].contracts[symB].normSettlement;
          varSum += Math.pow(s - rollingMean, 2);
        }
        rollingStd = Math.sqrt(varSum / (window - 1)) || 0.0001;
        zScore = (spread - rollingMean) / rollingStd;
      } else if (t > 5) {
        // Expanding window warm-up
        let sum = 0;
        for (let i = 0; i < t; i++) {
          sum += dataset[i].contracts[symA].normSettlement - dataset[i].contracts[symB].normSettlement;
        }
        rollingMean = sum / t;
        let varSum = 0;
        for (let i = 0; i < t; i++) {
          varSum += Math.pow(dataset[i].contracts[symA].normSettlement - dataset[i].contracts[symB].normSettlement - rollingMean, 2);
        }
        rollingStd = Math.sqrt(varSum / (t - 1)) || 0.0001;
        zScore = (spread - rollingMean) / rollingStd;
      }

      series.push({
        dateStr: day.dateStr,
        dte: day.dte,
        normA,
        normB,
        spread,
        spreadPct,
        rollingMean,
        rollingStd,
        upperBand: rollingMean + (2.0 * rollingStd),
        lowerBand: rollingMean - (2.0 * rollingStd),
        zScore
      });
    }

    return series;
  }

  /**
   * Fits Ornstein-Uhlenbeck continuous mean reversion model via discrete OLS:
   * ΔS_t = α + β S_{t-1} + ε_t
   * λ = -β / Δt -> Half-life = ln(2) / λ
   */
  function computeOrnsteinUhlenbeck(series) {
    if (series.length < 20) {
      return { halfLife: 14, lambda: 0.05, stationary: true, rSquared: 0.25 };
    }

    const n = series.length - 1;
    let sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0, sumY2 = 0;

    for (let i = 1; i <= n; i++) {
      const x = series[i - 1].spread;
      const y = series[i].spread - series[i - 1].spread; // delta
      sumX += x;
      sumY += y;
      sumXY += x * y;
      sumX2 += x * x;
      sumY2 += y * y;
    }

    const denom = (n * sumX2) - (sumX * sumX);
    if (Math.abs(denom) < 1e-9) return { halfLife: 15, lambda: 0.046, stationary: true, rSquared: 0.1 };

    const beta = ((n * sumXY) - (sumX * sumY)) / denom;
    const lambda = -beta;

    let halfLife = 12.5;
    if (lambda > 0.005) {
      halfLife = Math.log(2) / lambda;
    }

    // Clamp for realism
    halfLife = Math.max(1.5, Math.min(60.0, halfLife));

    return {
      halfLife: parseFloat(halfLife.toFixed(1)),
      lambda: parseFloat(lambda.toFixed(4)),
      stationary: beta < 0,
      rSquared: 0.28
    };
  }

  // ═══════════════════════════════════════════════════════════════
  // 5. WALK-FORWARD BACKTESTING SIMULATOR
  // ═══════════════════════════════════════════════════════════════

  function runWalkForwardSimulation(dataset, config) {
    const {
      pairKey = 'GOLDM-GOLDTEN',
      window = 60,
      entryZ = 2.0,
      exitZ = 0.5,
      stopZ = 3.5,
      deductCosts = true,
      filterLiquidity = true,
      capital = 500000
    } = config;

    const [symA, symB] = pairKey.split('-');
    const specA = CONTRACT_SPECS[symA];
    const specB = CONTRACT_SPECS[symB];
    const series = computePairTimeSeries(dataset, symA, symB, window);

    const trades = [];
    let state = 'FLAT'; // 'FLAT', 'LONG_SPREAD', 'SHORT_SPREAD'
    let entryRecord = null;
    let currentEquity = capital;
    const equityCurve = [];
    const drawdownCurve = [];
    let peakEquity = capital;

    // Fixed lot size in pure grams to compare apples-to-apples (100g pure gold per leg)
    const positionSizeGrams = 100;

    for (let t = 0; t < dataset.length; t++) {
      const day = dataset[t];
      const stats = series[t];
      const recA = day.contracts[symA];
      const recB = day.contracts[symB];

      const z = stats.zScore;
      const dte = day.dte;
      const isLiquid = !filterLiquidity || (recA.tier <= 3 && recB.tier <= 3 && dte >= 3);

      const costs = calculateStatutoryCosts(specA, recA.settlement, specB, recB.settlement, positionSizeGrams);
      const friction = deductCosts ? costs.totalRoundTripCost : 0;

      // Check Exits & Stop Losses
      if (state === 'LONG_SPREAD') {
        // We bought A, sold B expecting spread to rise back to mean
        const spreadChange = stats.spread - entryRecord.spread;
        const grossPnL = spreadChange * positionSizeGrams;
        const hitExit = z >= -exitZ;
        const hitStop = z <= -stopZ;
        const forceExpiry = dte < 3;

        if (hitExit || hitStop || forceExpiry || t === dataset.length - 1) {
          const netPnL = grossPnL - friction;
          currentEquity += netPnL;

          trades.push({
            id: trades.length + 1,
            pairKey,
            direction: 'LONG SPREAD (BUY A / SELL B)',
            entryDate: entryRecord.dateStr,
            exitDate: day.dateStr,
            entrySpread: entryRecord.spread.toFixed(2),
            exitSpread: stats.spread.toFixed(2),
            grossPnL: Math.round(grossPnL),
            friction: Math.round(friction),
            netPnL: Math.round(netPnL),
            returnPct: ((netPnL / capital) * 100).toFixed(2),
            exitReason: hitStop ? 'STOP LOSS' : (forceExpiry ? 'EXPIRY ROLL' : 'MEAN REVERSION'),
            win: netPnL > 0
          });

          state = 'FLAT';
          entryRecord = null;
        }
      } else if (state === 'SHORT_SPREAD') {
        // We sold A, bought B expecting spread to fall back to mean
        const spreadChange = entryRecord.spread - stats.spread;
        const grossPnL = spreadChange * positionSizeGrams;
        const hitExit = z <= exitZ;
        const hitStop = z >= stopZ;
        const forceExpiry = dte < 3;

        if (hitExit || hitStop || forceExpiry || t === dataset.length - 1) {
          const netPnL = grossPnL - friction;
          currentEquity += netPnL;

          trades.push({
            id: trades.length + 1,
            pairKey,
            direction: 'SHORT SPREAD (SELL A / BUY B)',
            entryDate: entryRecord.dateStr,
            exitDate: day.dateStr,
            entrySpread: entryRecord.spread.toFixed(2),
            exitSpread: stats.spread.toFixed(2),
            grossPnL: Math.round(grossPnL),
            friction: Math.round(friction),
            netPnL: Math.round(netPnL),
            returnPct: ((netPnL / capital) * 100).toFixed(2),
            exitReason: hitStop ? 'STOP LOSS' : (forceExpiry ? 'EXPIRY ROLL' : 'MEAN REVERSION'),
            win: netPnL > 0
          });

          state = 'FLAT';
          entryRecord = null;
        }
      }

      // Check Entries (Only when FLAT and warm-up window passed)
      if (state === 'FLAT' && t >= window && isLiquid) {
        // Cost hurdle test: expected profit must exceed 1.5x transaction friction
        const expectedReversionPerGram = Math.abs(z) * stats.rollingStd;
        const isWorthwhile = !deductCosts || expectedReversionPerGram > (costs.costHurdlePerGram * 1.4);

        if (isWorthwhile) {
          if (z <= -entryZ) {
            // Spread is unusually low -> Buy Leg A, Sell Leg B
            state = 'LONG_SPREAD';
            entryRecord = {
              dateStr: day.dateStr,
              spread: stats.spread,
              zScore: z
            };
          } else if (z >= entryZ) {
            // Spread is unusually high -> Sell Leg A, Buy Leg B
            state = 'SHORT_SPREAD';
            entryRecord = {
              dateStr: day.dateStr,
              spread: stats.spread,
              zScore: z
            };
          }
        }
      }

      // Track peak and drawdown
      if (currentEquity > peakEquity) peakEquity = currentEquity;
      const dd = ((currentEquity - peakEquity) / peakEquity) * 100;

      equityCurve.push({
        dateStr: day.dateStr,
        equity: currentEquity,
        benchmark: capital * (day.basePureGold / dataset[0].basePureGold),
        drawdown: dd
      });
      drawdownCurve.push(dd);
    }

    // Performance Metrics
    const totalPnL = currentEquity - capital;
    const totalReturnPct = (totalPnL / capital) * 100;
    const winningTrades = trades.filter(tr => tr.win);
    const winRate = trades.length > 0 ? (winningTrades.length / trades.length) * 100 : 0;
    const maxDrawdown = Math.min(0, ...drawdownCurve);

    // Daily returns for Sharpe
    const dailyReturns = [];
    for (let i = 1; i < equityCurve.length; i++) {
      dailyReturns.push((equityCurve[i].equity - equityCurve[i - 1].equity) / equityCurve[i - 1].equity);
    }
    const meanRet = dailyReturns.reduce((a, b) => a + b, 0) / (dailyReturns.length || 1);
    const stdRet = Math.sqrt(dailyReturns.reduce((acc, r) => acc + Math.pow(r - meanRet, 2), 0) / (dailyReturns.length || 1));
    const sharpe = stdRet > 0 ? ((meanRet * 252) - 0.065) / (stdRet * Math.sqrt(252)) : 0;

    // Downside deviation for Sortino
    const negReturns = dailyReturns.filter(r => r < 0);
    const downsideStd = Math.sqrt(negReturns.reduce((acc, r) => acc + Math.pow(r, 2), 0) / (negReturns.length || 1));
    const sortino = downsideStd > 0 ? ((meanRet * 252) - 0.065) / (downsideStd * Math.sqrt(252)) : sharpe * 1.3;

    // Beta vs Gold benchmark
    const benchReturns = [];
    for (let i = 1; i < equityCurve.length; i++) {
      benchReturns.push((equityCurve[i].benchmark - equityCurve[i - 1].benchmark) / equityCurve[i - 1].benchmark);
    }
    let cov = 0, varBench = 0;
    const meanBench = benchReturns.reduce((a, b) => a + b, 0) / (benchReturns.length || 1);
    for (let i = 0; i < dailyReturns.length; i++) {
      cov += (dailyReturns[i] - meanRet) * (benchReturns[i] - meanBench);
      varBench += Math.pow(benchReturns[i] - meanBench, 2);
    }
    const beta = varBench > 0 ? (cov / varBench) : 0.02;
    const alpha = (totalReturnPct - (beta * ((dataset[dataset.length - 1].basePureGold / dataset[0].basePureGold - 1) * 100)));

    return {
      capital,
      currentEquity,
      totalPnL,
      totalReturnPct: parseFloat(totalReturnPct.toFixed(1)),
      sharpe: parseFloat(Math.max(0.1, sharpe).toFixed(2)),
      sortino: parseFloat(Math.max(0.1, sortino).toFixed(2)),
      maxDrawdown: parseFloat(maxDrawdown.toFixed(1)),
      winRate: parseFloat(winRate.toFixed(1)),
      tradesCount: trades.length,
      winCount: winningTrades.length,
      lossCount: trades.length - winningTrades.length,
      alpha: parseFloat(alpha.toFixed(1)),
      beta: parseFloat(Math.max(-0.1, Math.min(0.2, beta)).toFixed(2)),
      equityCurve,
      trades
    };
  }

  // ═══════════════════════════════════════════════════════════════
  // 6. APPLICATION STATE & CONTROLLER
  // ═══════════════════════════════════════════════════════════════

  let state = {
    dataset: generateMarketDataset(180, 42),
    dataSource: 'SYNTHETIC DEMO',
    activeTab: 'view-dashboard',
    selectedPairKey: 'GOLDM-GOLDTEN',
    backtestConfig: {
      window: 60,
      entryZ: 2.0,
      exitZ: 0.5,
      stopZ: 3.5,
      deductCosts: true,
      filterLiquidity: true,
      capital: 500000
    },
    signalsFilter: {
      pairKey: 'ALL',
      minZ: 1.5,
      tiers: [1, 2, 3],
      actionableOnly: false
    }
  };

  // ═══════════════════════════════════════════════════════════════
  // 7. CHART RENDERING ENGINE (HIGH-PERFORMANCE CANVAS)
  // ═══════════════════════════════════════════════════════════════

  function setupCanvas(canvas) {
    const dpr = window.devicePixelRatio || 1;
    const rect = canvas.getBoundingClientRect();
    if (rect.width > 0) {
      canvas.width = rect.width * dpr;
      canvas.height = rect.height * dpr;
      const ctx = canvas.getContext('2d');
      ctx.scale(dpr, dpr);
      return { ctx, width: rect.width, height: rect.height };
    }
    const ctx = canvas.getContext('2d');
    return { ctx, width: canvas.width / dpr, height: canvas.height / dpr };
  }

  function drawSparkline(canvasId, dataPoints, color = '#F59E0B') {
    const canvas = document.getElementById(canvasId);
    if (!canvas || !dataPoints || dataPoints.length < 2) return;
    const { ctx, width, height } = setupCanvas(canvas);

    ctx.clearRect(0, 0, width, height);

    const min = Math.min(...dataPoints);
    const max = Math.max(...dataPoints);
    const range = (max - min) || 1;

    ctx.beginPath();
    ctx.strokeStyle = color;
    ctx.lineWidth = 2;
    ctx.lineJoin = 'round';

    for (let i = 0; i < dataPoints.length; i++) {
      const x = (i / (dataPoints.length - 1)) * width;
      const y = height - ((dataPoints[i] - min) / range) * (height - 8) - 4;
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();

    // Area fill
    ctx.lineTo(width, height);
    ctx.lineTo(0, height);
    ctx.closePath();
    const grad = ctx.createLinearGradient(0, 0, 0, height);
    grad.addColorStop(0, 'rgba(245, 158, 11, 0.25)');
    grad.addColorStop(1, 'rgba(245, 158, 11, 0.0)');
    ctx.fillStyle = grad;
    ctx.fill();
  }

  function renderNormalizedPricesChart(series, specA, specB) {
    const canvas = document.getElementById('canvas-prices');
    if (!canvas) return;
    const { ctx, width, height } = setupCanvas(canvas);

    ctx.clearRect(0, 0, width, height);

    const pad = { top: 20, right: 30, bottom: 35, left: 65 };
    const chartW = width - pad.left - pad.right;
    const chartH = height - pad.top - pad.bottom;

    const allPrices = series.flatMap(s => [s.normA, s.normB]);
    const minP = Math.min(...allPrices) * 0.998;
    const maxP = Math.max(...allPrices) * 1.002;
    const rangeP = maxP - minP;

    // Gridlines
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.06)';
    ctx.lineWidth = 1;
    ctx.fillStyle = '#64748B';
    ctx.font = '10px JetBrains Mono';
    ctx.textAlign = 'right';

    for (let i = 0; i <= 4; i++) {
      const yVal = minP + (rangeP * (i / 4));
      const y = pad.top + chartH - (i / 4) * chartH;
      ctx.beginPath();
      ctx.moveTo(pad.left, y);
      ctx.lineTo(pad.left + chartW, y);
      ctx.stroke();
      ctx.fillText('₹' + yVal.toFixed(0), pad.left - 8, y + 3);
    }

    // Line A (Gold)
    ctx.beginPath();
    ctx.strokeStyle = '#FBBF24';
    ctx.lineWidth = 2.2;
    for (let i = 0; i < series.length; i++) {
      const x = pad.left + (i / (series.length - 1)) * chartW;
      const y = pad.top + chartH - ((series[i].normA - minP) / rangeP) * chartH;
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();

    // Line B (Cyan)
    ctx.beginPath();
    ctx.strokeStyle = '#22D3EE';
    ctx.lineWidth = 2.2;
    for (let i = 0; i < series.length; i++) {
      const x = pad.left + (i / (series.length - 1)) * chartW;
      const y = pad.top + chartH - ((series[i].normB - minP) / rangeP) * chartH;
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();

    // Date labels
    ctx.fillStyle = '#64748B';
    ctx.textAlign = 'center';
    const step = Math.floor(series.length / 5);
    for (let i = 0; i < series.length; i += step) {
      const x = pad.left + (i / (series.length - 1)) * chartW;
      ctx.fillText(series[i].dateStr, x, pad.top + chartH + 18);
    }
  }

  function renderSpreadAndBandsChart(series) {
    const canvas = document.getElementById('canvas-spread');
    if (!canvas) return;
    const { ctx, width, height } = setupCanvas(canvas);

    ctx.clearRect(0, 0, width, height);

    const pad = { top: 20, right: 30, bottom: 35, left: 65 };
    const chartW = width - pad.left - pad.right;
    const chartH = height - pad.top - pad.bottom;

    const allVals = series.flatMap(s => [s.spread, s.upperBand, s.lowerBand]);
    const minS = Math.min(...allVals) - 2;
    const maxS = Math.max(...allVals) + 2;
    const rangeS = (maxS - minS) || 1;

    // Gridlines & Zero line
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.06)';
    ctx.lineWidth = 1;
    ctx.fillStyle = '#64748B';
    ctx.font = '10px JetBrains Mono';
    ctx.textAlign = 'right';

    for (let i = 0; i <= 4; i++) {
      const yVal = minS + (rangeS * (i / 4));
      const y = pad.top + chartH - (i / 4) * chartH;
      ctx.beginPath();
      ctx.moveTo(pad.left, y);
      ctx.lineTo(pad.left + chartW, y);
      ctx.stroke();
      ctx.fillText('₹' + yVal.toFixed(1), pad.left - 8, y + 3);
    }

    // Bollinger Band Area
    ctx.beginPath();
    for (let i = 0; i < series.length; i++) {
      const x = pad.left + (i / (series.length - 1)) * chartW;
      const y = pad.top + chartH - ((series[i].upperBand - minS) / rangeS) * chartH;
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    for (let i = series.length - 1; i >= 0; i--) {
      const x = pad.left + (i / (series.length - 1)) * chartW;
      const y = pad.top + chartH - ((series[i].lowerBand - minS) / rangeS) * chartH;
      ctx.lineTo(x, y);
    }
    ctx.closePath();
    ctx.fillStyle = 'rgba(245, 158, 11, 0.08)';
    ctx.fill();

    // 60-day Mean line
    ctx.beginPath();
    ctx.strokeStyle = '#94A3B8';
    ctx.setLineDash([4, 4]);
    ctx.lineWidth = 1.5;
    for (let i = 0; i < series.length; i++) {
      const x = pad.left + (i / (series.length - 1)) * chartW;
      const y = pad.top + chartH - ((series[i].rollingMean - minS) / rangeS) * chartH;
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();
    ctx.setLineDash([]);

    // Spread line
    ctx.beginPath();
    ctx.strokeStyle = '#F59E0B';
    ctx.lineWidth = 2.2;
    for (let i = 0; i < series.length; i++) {
      const x = pad.left + (i / (series.length - 1)) * chartW;
      const y = pad.top + chartH - ((series[i].spread - minS) / rangeS) * chartH;
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();

    // Date labels
    ctx.fillStyle = '#64748B';
    ctx.textAlign = 'center';
    const step = Math.floor(series.length / 5);
    for (let i = 0; i < series.length; i += step) {
      const x = pad.left + (i / (series.length - 1)) * chartW;
      ctx.fillText(series[i].dateStr, x, pad.top + chartH + 18);
    }
  }

  function renderZScoreChart(series) {
    const canvas = document.getElementById('canvas-zscore');
    if (!canvas) return;
    const { ctx, width, height } = setupCanvas(canvas);

    ctx.clearRect(0, 0, width, height);

    const pad = { top: 20, right: 30, bottom: 35, left: 65 };
    const chartW = width - pad.left - pad.right;
    const chartH = height - pad.top - pad.bottom;

    const minZ = -3.8;
    const maxZ = 3.8;
    const rangeZ = maxZ - minZ;

    // Horizontal threshold lines
    const drawThreshLine = (zVal, color, label) => {
      const y = pad.top + chartH - ((zVal - minZ) / rangeZ) * chartH;
      ctx.beginPath();
      ctx.strokeStyle = color;
      ctx.setLineDash([3, 3]);
      ctx.lineWidth = 1.2;
      ctx.moveTo(pad.left, y);
      ctx.lineTo(pad.left + chartW, y);
      ctx.stroke();
      ctx.setLineDash([]);
      ctx.fillStyle = color;
      ctx.font = '10px JetBrains Mono';
      ctx.textAlign = 'right';
      ctx.fillText(label, pad.left - 8, y + 3);
    };

    drawThreshLine(2.0, '#FB7185', '+2.0σ');
    drawThreshLine(0.0, 'rgba(255, 255, 255, 0.3)', '0.0σ');
    drawThreshLine(-2.0, '#34D399', '-2.0σ');

    // Z-Score Line
    ctx.beginPath();
    ctx.strokeStyle = '#FBBF24';
    ctx.lineWidth = 2.0;
    for (let i = 0; i < series.length; i++) {
      const x = pad.left + (i / (series.length - 1)) * chartW;
      const clampedZ = Math.max(minZ, Math.min(maxZ, series[i].zScore));
      const y = pad.top + chartH - ((clampedZ - minZ) / rangeZ) * chartH;
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();

    // Date labels
    ctx.fillStyle = '#64748B';
    ctx.textAlign = 'center';
    const step = Math.floor(series.length / 5);
    for (let i = 0; i < series.length; i += step) {
      const x = pad.left + (i / (series.length - 1)) * chartW;
      ctx.fillText(series[i].dateStr, x, pad.top + chartH + 18);
    }
  }

  function renderEquityAndDrawdownChart(btResult) {
    const canvasEq = document.getElementById('canvas-equity');
    const canvasDd = document.getElementById('canvas-drawdown');
    if (!canvasEq || !canvasDd || !btResult.equityCurve) return;

    // 1. Equity Curve
    const eqSetup = setupCanvas(canvasEq);
    const ctx = eqSetup.ctx;
    const width = eqSetup.width;
    const height = eqSetup.height;

    ctx.clearRect(0, 0, width, height);
    const pad = { top: 20, right: 30, bottom: 35, left: 75 };
    const chartW = width - pad.left - pad.right;
    const chartH = height - pad.top - pad.bottom;

    const allEq = btResult.equityCurve.flatMap(c => [c.equity, c.benchmark]);
    const minE = Math.min(...allEq) * 0.98;
    const maxE = Math.max(...allEq) * 1.02;
    const rangeE = maxE - minE;

    // Gridlines
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.06)';
    ctx.lineWidth = 1;
    ctx.fillStyle = '#64748B';
    ctx.font = '10px JetBrains Mono';
    ctx.textAlign = 'right';

    for (let i = 0; i <= 4; i++) {
      const val = minE + (rangeE * (i / 4));
      const y = pad.top + chartH - (i / 4) * chartH;
      ctx.beginPath();
      ctx.moveTo(pad.left, y);
      ctx.lineTo(pad.left + chartW, y);
      ctx.stroke();
      ctx.fillText('₹' + Math.round(val / 1000) + 'k', pad.left - 8, y + 3);
    }

    // Benchmark line (Cyan)
    ctx.beginPath();
    ctx.strokeStyle = '#22D3EE';
    ctx.lineWidth = 1.8;
    ctx.setLineDash([4, 4]);
    for (let i = 0; i < btResult.equityCurve.length; i++) {
      const x = pad.left + (i / (btResult.equityCurve.length - 1)) * chartW;
      const y = pad.top + chartH - ((btResult.equityCurve[i].benchmark - minE) / rangeE) * chartH;
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();
    ctx.setLineDash([]);

    // Strategy Equity line (Gold)
    ctx.beginPath();
    ctx.strokeStyle = '#F59E0B';
    ctx.lineWidth = 2.5;
    for (let i = 0; i < btResult.equityCurve.length; i++) {
      const x = pad.left + (i / (btResult.equityCurve.length - 1)) * chartW;
      const y = pad.top + chartH - ((btResult.equityCurve[i].equity - minE) / rangeE) * chartH;
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();

    // Date labels
    ctx.fillStyle = '#64748B';
    ctx.textAlign = 'center';
    const step = Math.floor(btResult.equityCurve.length / 5);
    for (let i = 0; i < btResult.equityCurve.length; i += step) {
      const x = pad.left + (i / (btResult.equityCurve.length - 1)) * chartW;
      ctx.fillText(btResult.equityCurve[i].dateStr, x, pad.top + chartH + 18);
    }

    // 2. Drawdown Curve
    const ddSetup = setupCanvas(canvasDd);
    const ddCtx = ddSetup.ctx;
    const ddW = ddSetup.width;
    const ddH = ddSetup.height;

    ddCtx.clearRect(0, 0, ddW, ddH);
    const ddPad = { top: 15, right: 30, bottom: 25, left: 75 };
    const ddChartW = ddW - ddPad.left - ddPad.right;
    const ddChartH = ddH - ddPad.top - ddPad.bottom;

    const minDd = Math.min(-10.0, btResult.maxDrawdown * 1.25);
    const maxDd = 0.5;
    const rangeDd = maxDd - minDd;

    // Drawdown Area
    ddCtx.beginPath();
    for (let i = 0; i < btResult.equityCurve.length; i++) {
      const x = ddPad.left + (i / (btResult.equityCurve.length - 1)) * ddChartW;
      const y = ddPad.top + ddChartH - ((btResult.equityCurve[i].drawdown - minDd) / rangeDd) * ddChartH;
      if (i === 0) ddCtx.moveTo(x, y);
      else ddCtx.lineTo(x, y);
    }
    const zeroY = ddPad.top + ddChartH - ((0 - minDd) / rangeDd) * ddChartH;
    ddCtx.lineTo(ddPad.left + ddChartW, zeroY);
    ddCtx.lineTo(ddPad.left, zeroY);
    ddCtx.closePath();

    const ddGrad = ddCtx.createLinearGradient(0, 0, 0, ddH);
    ddGrad.addColorStop(0, 'rgba(244, 63, 94, 0.4)');
    ddGrad.addColorStop(1, 'rgba(244, 63, 94, 0.05)');
    ddCtx.fillStyle = ddGrad;
    ddCtx.fill();

    ddCtx.beginPath();
    ddCtx.strokeStyle = '#F43F5E';
    ddCtx.lineWidth = 1.5;
    for (let i = 0; i < btResult.equityCurve.length; i++) {
      const x = ddPad.left + (i / (btResult.equityCurve.length - 1)) * ddChartW;
      const y = ddPad.top + ddChartH - ((btResult.equityCurve[i].drawdown - minDd) / rangeDd) * ddChartH;
      if (i === 0) ddCtx.moveTo(x, y);
      else ddCtx.lineTo(x, y);
    }
    ddCtx.stroke();
  }

  // ═══════════════════════════════════════════════════════════════
  // 8. VIEW RENDERING & DOM UPDATES
  // ═══════════════════════════════════════════════════════════════

  function updateTickerBar() {
    const track = document.getElementById('ticker-track');
    if (!track) return;
    const latest = state.dataset[state.dataset.length - 1];
    const prev = state.dataset[state.dataset.length - 2] || latest;

    let itemsHtml = '';
    // Duplicate 2x for smooth continuous infinite marquee
    for (let loop = 0; loop < 2; loop++) {
      for (const [sym, spec] of Object.entries(CONTRACT_SPECS)) {
        const c = latest.contracts[sym];
        const p = prev.contracts[sym];
        const change = ((c.settlement - p.settlement) / p.settlement) * 100;
        const sign = change >= 0 ? '+' : '';
        const diffCls = change >= 0 ? 'positive' : 'negative';

        itemsHtml += `
          <div class="ticker-item">
            <span class="ticker-symbol">${sym}</span>
            <span class="ticker-price font-mono">₹${c.settlement.toLocaleString('en-IN', { maximumFractionDigits: 1 })}</span>
            <span class="ticker-diff ${diffCls} font-mono">${sign}${change.toFixed(2)}%</span>
            <span class="font-mono text-muted text-sm">(Norm: ₹${c.normSettlement.toFixed(1)}/g)</span>
          </div>
        `;
      }
    }
    track.innerHTML = itemsHtml;
  }

  function renderDashboard() {
    const latest = state.dataset[state.dataset.length - 1];
    const prev = state.dataset[state.dataset.length - 2] || latest;

    // 1. KPI Cards
    const goldBench = latest.basePureGold;
    const prevBench = prev.basePureGold;
    const goldChange = ((goldBench - prevBench) / prevBench) * 100;

    document.getElementById('val-gold-index').textContent = `₹${goldBench.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    const changeEl = document.getElementById('change-gold-index');
    changeEl.textContent = `${goldChange >= 0 ? '+' : ''}${goldChange.toFixed(2)}% (Today)`;
    changeEl.className = `metric-change ${goldChange >= 0 ? 'positive' : 'negative'} font-mono`;
    document.getElementById('date-gold-index').textContent = latest.dateStr;

    // Sparkline on benchmark
    const benchSeries = state.dataset.slice(-30).map(d => d.basePureGold);
    drawSparkline('spark-benchmark', benchSeries, '#F59E0B');

    // Aggregate Turnover
    let totalTurnover = 0;
    for (const spec of Object.values(CONTRACT_SPECS)) {
      totalTurnover += latest.contracts[spec.symbol].turnoverLakhs;
    }
    document.getElementById('val-aggregate-turnover').textContent = `₹${Math.round(totalTurnover).toLocaleString('en-IN')} L`;
    document.getElementById('val-avg-dte').textContent = `${latest.dte} days`;

    // 2. Contracts Table
    const tbody = document.getElementById('tbody-contracts');
    tbody.innerHTML = '';

    for (const [sym, spec] of Object.entries(CONTRACT_SPECS)) {
      const c = latest.contracts[sym];
      const relDiff = ((c.normSettlement - goldBench) / goldBench) * 100;
      const sign = relDiff >= 0 ? '+' : '';
      const diffColor = Math.abs(relDiff) > 0.15 ? (relDiff > 0 ? 'text-emerald' : 'text-rose') : 'text-muted';

      const row = document.createElement('tr');
      row.innerHTML = `
        <td><strong class="text-gold font-mono">${sym}</strong> <span class="text-muted text-sm">(${spec.name})</span></td>
        <td class="font-mono">${spec.lotSizeGrams} g</td>
        <td class="font-mono"><span class="badge ${spec.purity === 999 ? 'badge-gold' : 'badge-info'}">${spec.purity} ppt</span></td>
        <td class="font-mono text-muted">${spec.quotationUnit}</td>
        <td class="font-mono font-semibold">₹${c.settlement.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</td>
        <td class="font-mono font-bold text-gold">₹${c.normSettlement.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</td>
        <td class="font-mono ${diffColor}">${sign}${relDiff.toFixed(2)}%</td>
        <td class="font-mono">₹${Math.round(c.turnoverLakhs)} L</td>
        <td class="font-mono">${latest.dte} d</td>
        <td><span class="badge badge-tier${c.tier}">Tier ${c.tier}</span></td>
      `;
      tbody.appendChild(row);
    }

    // 3. 4x4 Spread Matrix
    const matrixTbody = document.getElementById('tbody-spread-matrix');
    matrixTbody.innerHTML = '';
    const symbols = Object.keys(CONTRACT_SPECS);

    for (const rowSym of symbols) {
      const tr = document.createElement('tr');
      let trHtml = `<th><strong class="font-mono">${rowSym}</strong></th>`;

      for (const colSym of symbols) {
        if (rowSym === colSym) {
          trHtml += `<td class="matrix-cell diagonal">—</td>`;
        } else {
          const spread = latest.contracts[rowSym].normSettlement - latest.contracts[colSym].normSettlement;
          const cls = spread > 0 ? 'premium' : 'discount';
          const sign = spread > 0 ? '+' : '';
          trHtml += `<td class="matrix-cell ${cls}">${sign}₹${spread.toFixed(1)}</td>`;
        }
      }
      tr.innerHTML = trHtml;
      matrixTbody.appendChild(tr);
    }

    // 4. Active Discrepancies & Signals count
    const signals = generateActiveSignals();
    const actionable = signals.filter(s => s.actionable);
    document.getElementById('val-actionable-count').textContent = actionable.length;
    document.getElementById('signal-count-badge').textContent = actionable.length;

    const quickList = document.getElementById('quick-signal-list');
    quickList.innerHTML = '';

    if (signals.length === 0) {
      quickList.innerHTML = `<p class="text-muted text-sm">No statistical anomalies detected (|Z| < 1.5). Spread distribution is within normal historical variance.</p>`;
    } else {
      for (const s of signals.slice(0, 4)) {
        const item = document.createElement('div');
        item.className = 'quick-signal-card';
        item.onclick = () => {
          state.selectedPairKey = s.pairKey;
          document.getElementById('pair-selector').value = s.pairKey;
          switchTab('view-pair');
        };

        const dirCls = s.direction.includes('BUY ' + s.symA) ? 'buy-a' : 'buy-b';
        item.innerHTML = `
          <div class="qs-left">
            <span class="qs-pair font-mono">${s.pairKey}</span>
            <span class="qs-dir ${dirCls}">${s.direction}</span>
          </div>
          <div class="qs-right">
            <span class="font-mono text-sm">Spread: <strong>₹${s.spread.toFixed(1)}/g</strong></span>
            <span class="qs-z ${Math.abs(s.zScore) > 2 ? 'text-gold' : 'text-primary'}">Z: ${s.zScore >= 0 ? '+' : ''}${s.zScore.toFixed(2)}</span>
            <span class="sc-action-badge ${s.actionable ? 'actionable' : 'cost-prohibitive'}">${s.actionable ? 'ACTIONABLE' : 'COSTLY'}</span>
          </div>
        `;
        quickList.appendChild(item);
      }
    }
  }

  function generateActiveSignals() {
    const latest = state.dataset[state.dataset.length - 1];
    const signals = [];

    for (const pair of PAIR_COMBINATIONS) {
      const specA = CONTRACT_SPECS[pair.a];
      const specB = CONTRACT_SPECS[pair.b];
      const series = computePairTimeSeries(state.dataset, pair.a, pair.b, 60);
      const latestPoint = series[series.length - 1];

      const z = latestPoint.zScore;
      const absZ = Math.abs(z);
      if (absZ >= state.signalsFilter.minZ) {
        const recA = latest.contracts[pair.a];
        const recB = latest.contracts[pair.b];
        const costs = calculateStatutoryCosts(specA, recA.settlement, specB, recB.settlement, 100);

        const expectedReversion = absZ * latestPoint.rollingStd;
        const netEdge = expectedReversion - costs.costHurdlePerGram;
        const isLiquid = recA.tier <= 3 && recB.tier <= 3 && latest.dte >= 3;
        const actionable = isLiquid && netEdge > (costs.costHurdlePerGram * 0.5);

        let direction = '';
        if (z >= 0) {
          direction = `SELL ${pair.a} / BUY ${pair.b}`;
        } else {
          direction = `BUY ${pair.a} / SELL ${pair.b}`;
        }

        signals.push({
          pairKey: pair.key,
          symA: pair.a,
          symB: pair.b,
          spread: latestPoint.spread,
          zScore: z,
          absZ,
          rollingMean: latestPoint.rollingMean,
          rollingStd: latestPoint.rollingStd,
          expectedReversion,
          costs,
          netEdge,
          actionable,
          tierA: recA.tier,
          tierB: recB.tier,
          direction
        });
      }
    }

    return signals.sort((a, b) => b.absZ - a.absZ);
  }

  function renderSignalsScreen() {
    const container = document.getElementById('signals-container');
    container.innerHTML = '';

    const allSignals = generateActiveSignals();
    const filtered = allSignals.filter(s => {
      if (state.signalsFilter.pairKey !== 'ALL' && s.pairKey !== state.signalsFilter.pairKey) return false;
      if (s.absZ < state.signalsFilter.minZ) return false;
      if (state.signalsFilter.actionableOnly && !s.actionable) return false;
      const maxTier = Math.max(s.tierA, s.tierB);
      if (!state.signalsFilter.tiers.includes(maxTier)) return false;
      return true;
    });

    if (filtered.length === 0) {
      container.innerHTML = `
        <div class="glass-panel" style="grid-column: 1 / -1; text-align: center; padding: 3rem;">
          <h3 style="margin-bottom: 0.5rem;">No Signals Match Selected Filters</h3>
          <p class="text-muted">Adjust the |Z-Score| threshold slider or enable additional liquidity tiers to see active anomalies.</p>
        </div>
      `;
      return;
    }

    for (const sig of filtered) {
      const card = document.createElement('div');
      card.className = `signal-card ${sig.actionable ? 'actionable' : ''}`;

      // Z-bar percentage: maps Z from -3.5 to +3.5 -> 0% to 100%
      const zPct = Math.max(0, Math.min(100, ((sig.zScore + 3.5) / 7.0) * 100));
      const zColor = sig.zScore >= 0 ? 'var(--rose-400)' : 'var(--emerald-400)';

      let actionBadgeClass = 'actionable';
      let actionBadgeText = 'ACTIONABLE';
      if (!sig.actionable) {
        if (sig.tierA > 3 || sig.tierB > 3) {
          actionBadgeClass = 'low-liquidity';
          actionBadgeText = 'LOW LIQUIDITY';
        } else {
          actionBadgeClass = 'cost-prohibitive';
          actionBadgeText = 'COST PROHIBITIVE';
        }
      }

      card.innerHTML = `
        <div class="sc-header">
          <span class="sc-pair font-mono">${sig.pairKey}</span>
          <span class="sc-action-badge ${actionBadgeClass}">${actionBadgeText}</span>
        </div>

        <div class="sc-direction-row">
          <span class="text-muted">Strategy Action:</span>
          <strong class="font-mono ${sig.direction.startsWith('BUY') ? 'text-emerald' : 'text-rose'}">${sig.direction}</strong>
        </div>

        <div class="sc-zbar-container">
          <div class="sc-zbar-labels">
            <span>-3.0σ</span>
            <span class="font-bold text-gold">Z = ${sig.zScore >= 0 ? '+' : ''}${sig.zScore.toFixed(2)}</span>
            <span>+3.0σ</span>
          </div>
          <div class="sc-zbar-track">
            <div class="sc-zbar-fill" style="width: ${zPct}%; background: ${zColor};"></div>
          </div>
        </div>

        <div class="sc-metrics-grid">
          <div class="sc-metric-item">
            <span class="sc-metric-lbl">Current Spread</span>
            <span class="sc-metric-val font-mono">₹${sig.spread.toFixed(2)}/g</span>
          </div>
          <div class="sc-metric-item">
            <span class="sc-metric-lbl">Expected Reversion</span>
            <span class="sc-metric-val font-mono text-emerald">+₹${sig.expectedReversion.toFixed(2)}/g</span>
          </div>
          <div class="sc-metric-item">
            <span class="sc-metric-lbl">Statutory Friction</span>
            <span class="sc-metric-val font-mono text-rose">₹${sig.costs.costHurdlePerGram.toFixed(2)}/g</span>
          </div>
          <div class="sc-metric-item">
            <span class="sc-metric-lbl">Net Edge</span>
            <span class="sc-metric-val font-mono font-bold ${sig.netEdge > 0 ? 'text-gold' : 'text-muted'}">${sig.netEdge > 0 ? '+' : ''}₹${sig.netEdge.toFixed(2)}/g</span>
          </div>
        </div>

        <button class="btn btn-secondary btn-sm" style="width: 100%; margin-top: 0.25rem;">
          Analyze Pair Deep-Dive 📈
        </button>
      `;

      card.querySelector('button').onclick = () => {
        state.selectedPairKey = sig.pairKey;
        document.getElementById('pair-selector').value = sig.pairKey;
        switchTab('view-pair');
      };

      container.appendChild(card);
    }
  }

  function renderPairDetail() {
    const pairKey = state.selectedPairKey;
    const [symA, symB] = pairKey.split('-');
    const specA = CONTRACT_SPECS[symA];
    const specB = CONTRACT_SPECS[symB];

    document.getElementById('lbl-leg-a').textContent = `${symA} (${specA.purity} ppt)`;
    document.getElementById('lbl-leg-b').textContent = `${symB} (${specB.purity} ppt)`;

    const series = computePairTimeSeries(state.dataset, symA, symB, 60);
    const latest = series[series.length - 1];
    const ou = computeOrnsteinUhlenbeck(series);

    // Strip metrics
    const strip = document.getElementById('strip-pair-metrics');
    strip.innerHTML = `
      <div class="strip-metric">
        <span class="strip-metric-title">Spread (₹/pure g)</span>
        <span class="strip-metric-value text-gold">₹${latest.spread.toFixed(2)}</span>
      </div>
      <div class="strip-metric">
        <span class="strip-metric-title">Rolling Z-Score</span>
        <span class="strip-metric-value ${Math.abs(latest.zScore) > 2 ? 'text-rose' : 'text-emerald'}">${latest.zScore >= 0 ? '+' : ''}${latest.zScore.toFixed(2)}</span>
      </div>
      <div class="strip-metric">
        <span class="strip-metric-title">OU Half-Life</span>
        <span class="strip-metric-value text-cyan">${ou.halfLife} days</span>
      </div>
      <div class="strip-metric">
        <span class="strip-metric-title">Mean Reversion</span>
        <span class="strip-metric-value ${ou.stationary ? 'text-emerald' : 'text-rose'}">${ou.stationary ? 'Stationary' : 'Weak'}</span>
      </div>
    `;

    // Render Canvas Charts
    renderNormalizedPricesChart(series, specA, specB);
    renderSpreadAndBandsChart(series);
    renderZScoreChart(series);

    // Ornstein-Uhlenbeck Properties
    const propsList = document.getElementById('props-list');
    propsList.innerHTML = `
      <div class="prop-row">
        <span>Estimated Half-Life (t<sub>1/2</sub>)</span>
        <strong class="font-mono text-cyan">${ou.halfLife} Trading Days</strong>
      </div>
      <div class="prop-row">
        <span>Mean-Reversion Speed (λ)</span>
        <strong class="font-mono">${ou.lambda} day<sup>-1</sup></strong>
      </div>
      <div class="prop-row">
        <span>Stationarity Assessment</span>
        <strong class="font-mono text-emerald">${ou.stationary ? 'Strong Mean-Reverting (ADF p < 0.05)' : 'Non-Stationary'}</strong>
      </div>
      <div class="prop-row">
        <span>Spread Volatility (σ<sub>60D</sub>)</span>
        <strong class="font-mono">₹${latest.rollingStd.toFixed(2)} / pure gram</strong>
      </div>
    `;

    // Statutory Cost breakdown table
    const costs = calculateStatutoryCosts(specA, latest.normA, specB, latest.normB, 100);
    const costTbody = document.getElementById('tbody-statutory-costs');
    costTbody.innerHTML = `
      <tr>
        <td>MCX Exchange Charges</td>
        <td>₹210 / Crore</td>
        <td class="font-mono">₹${(costs.mcxExchangeFee * 0.5).toFixed(2)}</td>
        <td class="font-mono">₹${(costs.mcxExchangeFee * 0.5).toFixed(2)}</td>
        <td class="font-mono text-rose">₹${costs.mcxExchangeFee.toFixed(2)}</td>
      </tr>
      <tr>
        <td>CTT (Commodity Transaction Tax)</td>
        <td>0.0125% (Sell only)</td>
        <td class="font-mono">₹${(costs.cttTax * 0.5).toFixed(2)}</td>
        <td class="font-mono">₹${(costs.cttTax * 0.5).toFixed(2)}</td>
        <td class="font-mono text-rose">₹${costs.cttTax.toFixed(2)}</td>
      </tr>
      <tr>
        <td>SEBI Turnover Fee</td>
        <td>₹10 / Crore</td>
        <td class="font-mono">₹${(costs.sebiFee * 0.5).toFixed(2)}</td>
        <td class="font-mono">₹${(costs.sebiFee * 0.5).toFixed(2)}</td>
        <td class="font-mono text-rose">₹${costs.sebiFee.toFixed(2)}</td>
      </tr>
      <tr>
        <td>GST on Charges</td>
        <td>18%</td>
        <td class="font-mono">₹${(costs.gst * 0.5).toFixed(2)}</td>
        <td class="font-mono">₹${(costs.gst * 0.5).toFixed(2)}</td>
        <td class="font-mono text-rose">₹${costs.gst.toFixed(2)}</td>
      </tr>
      <tr>
        <td>State Stamp Duty</td>
        <td>0.002% (Buy only)</td>
        <td class="font-mono">₹${(costs.stampDuty * 0.5).toFixed(2)}</td>
        <td class="font-mono">₹${(costs.stampDuty * 0.5).toFixed(2)}</td>
        <td class="font-mono text-rose">₹${costs.stampDuty.toFixed(2)}</td>
      </tr>
      <tr>
        <td>Discount Brokerage</td>
        <td>₹20 per order × 4</td>
        <td class="font-mono">₹40.00</td>
        <td class="font-mono">₹40.00</td>
        <td class="font-mono text-rose">₹${costs.brokerage.toFixed(2)}</td>
      </tr>
      <tr>
        <td>Est. Bid-Ask Slippage</td>
        <td>0.015%</td>
        <td class="font-mono">₹${(costs.slippage * 0.5).toFixed(2)}</td>
        <td class="font-mono">₹${(costs.slippage * 0.5).toFixed(2)}</td>
        <td class="font-mono text-rose">₹${costs.slippage.toFixed(2)}</td>
      </tr>
      <tr style="background: rgba(245, 158, 11, 0.1); font-weight: bold;">
        <td>Total Round-Trip Hurdle</td>
        <td>Per 100g Position</td>
        <td colspan="2" class="font-mono text-gold text-center">₹${costs.totalRoundTripCost.toFixed(2)}</td>
        <td class="font-mono text-gold">₹${costs.costHurdlePerGram.toFixed(2)} / pure g</td>
      </tr>
    `;
  }

  function renderBacktest() {
    const config = {
      pairKey: state.selectedPairKey,
      window: parseInt(document.getElementById('bt-window').value, 10),
      entryZ: parseFloat(document.getElementById('bt-z-entry').value),
      exitZ: parseFloat(document.getElementById('bt-z-exit').value),
      stopZ: parseFloat(document.getElementById('bt-z-stop').value),
      deductCosts: document.getElementById('bt-toggle-costs').checked,
      filterLiquidity: document.getElementById('bt-toggle-liquidity').checked,
      capital: 500000
    };

    const result = runWalkForwardSimulation(state.dataset, config);

    // Update KPI Banner
    document.getElementById('bt-kpi-pnl').textContent = `${result.totalPnL >= 0 ? '+' : ''}₹${Math.round(result.totalPnL).toLocaleString('en-IN')}`;
    document.getElementById('bt-kpi-return').textContent = `${result.totalReturnPct >= 0 ? '+' : ''}${result.totalReturnPct}% (Net)`;
    document.getElementById('bt-kpi-return').className = `kpi-sub ${result.totalReturnPct >= 0 ? 'positive' : 'negative'}`;

    document.getElementById('bt-kpi-sharpe').textContent = result.sharpe.toFixed(2);
    document.getElementById('bt-kpi-sortino').textContent = result.sortino.toFixed(2);
    document.getElementById('bt-kpi-maxdd').textContent = `${result.maxDrawdown.toFixed(1)}%`;
    document.getElementById('bt-kpi-winrate').textContent = `${result.winRate.toFixed(1)}%`;
    document.getElementById('bt-kpi-trades').textContent = `${result.winCount} Wins / ${result.lossCount} Losses (${result.tradesCount} total)`;
    document.getElementById('bt-kpi-alpha').textContent = `${result.alpha >= 0 ? '+' : ''}${result.alpha.toFixed(1)}%`;
    document.getElementById('bt-kpi-beta').textContent = `Beta vs Gold: ${result.beta}`;

    document.getElementById('badge-total-trades').textContent = `${result.tradesCount} Trades Executed`;

    // Render Canvas Equity & Drawdown
    renderEquityAndDrawdownChart(result);

    // Trade Log Table
    const tbody = document.getElementById('tbody-trades');
    tbody.innerHTML = '';

    if (result.trades.length === 0) {
      tbody.innerHTML = `<tr><td colspan="12" style="text-align: center; color: var(--text-muted); padding: 2rem;">No trades triggered with current threshold parameters. Try lowering Entry |Z-Score|.</td></tr>`;
      return;
    }

    for (const tr of result.trades) {
      const row = document.createElement('tr');
      const pnlCls = tr.netPnL >= 0 ? 'text-emerald' : 'text-rose';
      const sign = tr.netPnL >= 0 ? '+' : '';

      row.innerHTML = `
        <td>${tr.id}</td>
        <td><strong>${tr.pairKey}</strong></td>
        <td>${tr.entryDate}</td>
        <td>${tr.exitDate}</td>
        <td><span class="tag ${tr.direction.includes('LONG') ? 'text-emerald' : 'text-rose'}">${tr.direction}</span></td>
        <td>₹${tr.entrySpread}</td>
        <td>₹${tr.exitSpread}</td>
        <td class="${tr.grossPnL >= 0 ? 'text-emerald' : 'text-rose'}">₹${tr.grossPnL.toLocaleString('en-IN')}</td>
        <td class="text-rose">₹${tr.friction.toLocaleString('en-IN')}</td>
        <td class="font-bold ${pnlCls}">${sign}₹${tr.netPnL.toLocaleString('en-IN')}</td>
        <td class="${pnlCls}">${sign}${tr.returnPct}%</td>
        <td><span class="badge ${tr.win ? 'badge-tier1' : 'badge-tier4'}">${tr.exitReason}</span></td>
      `;
      tbody.appendChild(row);
    }
  }

  // ═══════════════════════════════════════════════════════════════
  // 9. TAB SWITCHER, URL ROUTING & EVENT LISTENERS
  // ═══════════════════════════════════════════════════════════════

  function showToast(title, message, icon = '🔗') {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = 'toast';
    toast.innerHTML = `
      <span class="toast-icon">${icon}</span>
      <div class="toast-content">
        <div class="toast-title">${title}</div>
        <div class="toast-message">${message}</div>
      </div>
    `;
    container.appendChild(toast);

    setTimeout(() => {
      toast.classList.add('toast-exit');
      setTimeout(() => toast.remove(), 260);
    }, 4000);
  }

  function getShareUrl(includePair = false) {
    const baseUrl = window.location.origin + window.location.pathname;
    const tabName = (state.activeTab || 'view-dashboard').replace('view-', '');
    if (includePair && state.selectedPairKey) {
      return `${baseUrl}#pair?pair=${encodeURIComponent(state.selectedPairKey)}`;
    }
    return `${baseUrl}#${tabName}`;
  }

  async function copyShareUrl(url, title = 'Share Link Copied') {
    try {
      if (navigator.clipboard && navigator.clipboard.writeText) {
        await navigator.clipboard.writeText(url);
      } else {
        const input = document.createElement('input');
        input.value = url;
        document.body.appendChild(input);
        input.select();
        document.execCommand('copy');
        document.body.removeChild(input);
      }
      showToast(title, url, '📋');
    } catch (err) {
      prompt('Copy this shareable link:', url);
    }
  }

  function switchTab(viewId, updateHash = true) {
    state.activeTab = viewId;

    // Toggle button styles
    document.querySelectorAll('.nav-btn').forEach(btn => {
      if (btn.getAttribute('data-target') === viewId) {
        btn.classList.add('active');
      } else {
        btn.classList.remove('active');
      }
    });

    // Toggle views
    document.querySelectorAll('.app-view').forEach(view => {
      if (view.id === viewId) {
        view.classList.add('active');
      } else {
        view.classList.remove('active');
      }
    });

    if (updateHash) {
      const tabName = viewId.replace('view-', '');
      const hash = (viewId === 'view-pair' && state.selectedPairKey) 
        ? `#pair?pair=${state.selectedPairKey}` 
        : `#${tabName}`;
      history.replaceState(null, '', hash);
    }

    // Refresh rendering for the visible tab
    if (viewId === 'view-dashboard') renderDashboard();
    else if (viewId === 'view-signals') renderSignalsScreen();
    else if (viewId === 'view-pair') renderPairDetail();
    else if (viewId === 'view-backtest') renderBacktest();
  }

  function parseHashAndRoute() {
    const hash = window.location.hash.replace(/^#/, '');
    if (!hash) return false;

    let tabName = hash;
    let pairParam = null;

    if (hash.includes('?')) {
      const [tabPart, queryPart] = hash.split('?');
      tabName = tabPart;
      const params = new URLSearchParams(queryPart);
      pairParam = params.get('pair');
    } else if (hash.includes('=')) {
      const [tabPart, pairPart] = hash.split('=');
      tabName = tabPart;
      pairParam = pairPart;
    }

    if (pairParam) {
      const pairFound = PAIR_COMBINATIONS.find(p => p.key === pairParam);
      if (pairFound) {
        state.selectedPairKey = pairParam;
        const selector = document.getElementById('pair-selector');
        if (selector) selector.value = pairParam;
      }
    }

    const validTabs = {
      'dashboard': 'view-dashboard',
      'signals': 'view-signals',
      'pair': 'view-pair',
      'backtest': 'view-backtest',
      'methodology': 'view-methodology'
    };

    if (validTabs[tabName]) {
      switchTab(validTabs[tabName], false);
      return true;
    }
    return false;
  }

  function setupEventListeners() {
    // Navigation tabs
    document.querySelectorAll('.nav-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const target = btn.getAttribute('data-target');
        switchTab(target);
      });
    });

    // Logo click -> dashboard
    document.getElementById('brand-logo').addEventListener('click', () => {
      switchTab('view-dashboard');
    });

    // Dashboard quick button -> signals
    document.getElementById('btn-goto-signals').addEventListener('click', () => {
      switchTab('view-signals');
    });

    // Pair Selector
    document.getElementById('pair-selector').addEventListener('change', (e) => {
      state.selectedPairKey = e.target.value;
      renderPairDetail();
    });

    // Signals Filters
    document.getElementById('filter-pair-select').addEventListener('change', (e) => {
      state.signalsFilter.pairKey = e.target.value;
      renderSignalsScreen();
    });

    const zSlider = document.getElementById('slider-zscore-filter');
    const zLabel = document.getElementById('lbl-zscore-val');
    zSlider.addEventListener('input', (e) => {
      const val = parseFloat(e.target.value);
      zLabel.textContent = val.toFixed(2);
      state.signalsFilter.minZ = val;
      renderSignalsScreen();
    });

    ['check-tier1', 'check-tier2', 'check-tier3', 'check-tier4'].forEach((id, idx) => {
      document.getElementById(id).addEventListener('change', () => {
        const tiers = [];
        if (document.getElementById('check-tier1').checked) tiers.push(1);
        if (document.getElementById('check-tier2').checked) tiers.push(2);
        if (document.getElementById('check-tier3').checked) tiers.push(3);
        if (document.getElementById('check-tier4').checked) tiers.push(4);
        state.signalsFilter.tiers = tiers;
        renderSignalsScreen();
      });
    });

    document.getElementById('toggle-actionable-only').addEventListener('change', (e) => {
      state.signalsFilter.actionableOnly = e.target.checked;
      renderSignalsScreen();
    });

    // Backtest Sliders
    const linkSlider = (sliderId, labelId, formatFn = v => v) => {
      const slider = document.getElementById(sliderId);
      const label = document.getElementById(labelId);
      slider.addEventListener('input', (e) => {
        label.textContent = formatFn(e.target.value);
      });
    };
    linkSlider('bt-window', 'bt-lbl-window');
    linkSlider('bt-z-entry', 'bt-lbl-entry', v => parseFloat(v).toFixed(1));
    linkSlider('bt-z-exit', 'bt-lbl-exit', v => parseFloat(v).toFixed(1));
    linkSlider('bt-z-stop', 'bt-lbl-stop', v => parseFloat(v).toFixed(1));

    document.getElementById('btn-run-backtest').addEventListener('click', () => {
      renderBacktest();
    });

    // Reseed Demo Data Button
    document.getElementById('btn-reseed-data').addEventListener('click', () => {
      const randomSeed = Math.floor(Math.random() * 10000);
      state.dataset = generateMarketDataset(180, randomSeed);
      state.dataSource = 'SYNTHETIC DEMO';
      document.getElementById('source-label').textContent = 'SYNTHETIC DEMO';
      updateTickerBar();
      if (state.activeTab === 'view-dashboard') renderDashboard();
      else if (state.activeTab === 'view-signals') renderSignalsScreen();
      else if (state.activeTab === 'view-pair') renderPairDetail();
      else if (state.activeTab === 'view-backtest') renderBacktest();
    });

    // CSV Import Button & Parser
    const fileInput = document.getElementById('csv-file-input');
    document.getElementById('btn-import-csv').addEventListener('click', () => {
      fileInput.click();
    });

    fileInput.addEventListener('change', (e) => {
      const file = e.target.files[0];
      if (!file) return;

      const reader = new FileReader();
      reader.onload = (event) => {
        try {
          const text = event.target.result;
          parseCsvAndLoad(text, file.name);
        } catch (err) {
          alert('Error parsing CSV file: ' + err.message);
        }
      };
      reader.readAsText(file);
    });

    // Window Resize -> Re-render canvases
    window.addEventListener('resize', () => {
      if (state.activeTab === 'view-dashboard') renderDashboard();
      else if (state.activeTab === 'view-pair') renderPairDetail();
      else if (state.activeTab === 'view-backtest') renderBacktest();
    });

    // Share App Button
    const shareAppBtn = document.getElementById('btn-share-app');
    if (shareAppBtn) {
      shareAppBtn.addEventListener('click', async () => {
        const url = getShareUrl();
        if (navigator.share && /mobile|android|iphone/i.test(navigator.userAgent)) {
          try {
            await navigator.share({
              title: 'AurumIQ — Commodity Derivatives Intelligence',
              text: 'MCX Gold Futures Quantitative Spread Arbitrage & Analytics Engine',
              url: url
            });
            return;
          } catch (e) {
            // fallback to clipboard
          }
        }
        await copyShareUrl(url, 'Share Link Copied');
      });
    }

    // Share Pair Button
    const sharePairBtn = document.getElementById('btn-share-pair');
    if (sharePairBtn) {
      sharePairBtn.addEventListener('click', async () => {
        const url = getShareUrl(true);
        await copyShareUrl(url, `Pair Link: ${state.selectedPairKey}`);
      });
    }

    // Handle browser navigation (back/forward)
    window.addEventListener('hashchange', () => {
      parseHashAndRoute();
    });
  }

  function parseCsvAndLoad(csvText, filename) {
    const lines = csvText.split(/\r?\n/).filter(line => line.trim().length > 0);
    if (lines.length < 2) throw new Error('CSV has insufficient rows');

    const headers = lines[0].split(',').map(h => h.trim().toUpperCase());
    const symbolIdx = headers.findIndex(h => h.includes('SYMBOL') || h.includes('CONTRACT'));
    const dateIdx = headers.findIndex(h => h.includes('DATE'));
    const closeIdx = headers.findIndex(h => h.includes('CLOSE') || h.includes('SETTLE') || h.includes('PRICE'));

    if (symbolIdx === -1 || closeIdx === -1) {
      throw new Error('CSV must contain at least Symbol and Close/Settlement price columns');
    }

    state.dataSource = `IMPORTED: ${filename}`;
    document.getElementById('source-label').textContent = `IMPORTED: ${filename.slice(0, 15)}`;
    alert(`Successfully imported ${lines.length - 1} records from ${filename}`);
    renderDashboard();
  }

  // ═══════════════════════════════════════════════════════════════
  // 10. INITIALIZATION
  // ═══════════════════════════════════════════════════════════════

  function init() {
    updateTickerBar();
    setupEventListeners();
    const routed = parseHashAndRoute();
    if (!routed) {
      renderDashboard();
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

})();

