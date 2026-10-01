package com.aurumiq.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aurumiq.app.data.model.SignalCategory
import com.aurumiq.app.engine.CostEstimator
import com.aurumiq.app.data.model.Settings
import com.aurumiq.app.ui.components.*
import com.aurumiq.app.ui.theme.*
import com.aurumiq.app.ui.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairDetailScreen(
    viewModel: MainViewModel,
    symbolA: String,
    symbolB: String,
    onBack: () -> Unit,
    onNavigateToBacktest: () -> Unit
) {
    val pairDetailState by viewModel.pairDetailState.collectAsState()
    val dataSource by viewModel.dataSource.collectAsState()
    val settings by viewModel.settings.collectAsState()

    LaunchedEffect(symbolA, symbolB) {
        viewModel.selectPair(symbolA, symbolB)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("$symbolA vs $symbolB", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                        Text("Pair Analysis", style = MaterialTheme.typography.bodySmall, color = TextMuted)
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item { DataSourceBadge(dataSource, Modifier.fillMaxWidth()) }

            when (val state = pairDetailState) {
                is PairDetailState.Loading -> {
                    item {
                        Box(Modifier.fillMaxWidth().height(200.dp), Alignment.Center) {
                            CircularProgressIndicator(color = GoldPrimary)
                        }
                    }
                }

                is PairDetailState.Error -> {
                    item {
                        Surface(shape = RoundedCornerShape(12.dp), color = SignalRed.copy(0.1f)) {
                            Text(state.message, Modifier.padding(16.dp), color = SignalRed)
                        }
                    }
                }

                is PairDetailState.Empty -> {
                    item { Text("Select a pair to analyze", color = TextMuted) }
                }

                is PairDetailState.Loaded -> {
                    val signals = state.signals
                    if (signals.isEmpty()) {
                        item { Text("No data available for this pair", color = TextMuted) }
                        return@LazyColumn
                    }

                    val latest = signals.last()

                    // Current Status Cards
                    item {
                        SectionHeader(title = "Current Status", labelType = "CALCULATED")
                    }

                    item {
                        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                            StatCard(
                                title = "Spread",
                                value = formatPercent(latest.spreadPercent),
                                valueColor = colorForValue(latest.spreadPercent),
                                labelType = "CALCULATED",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Z-Score",
                                value = String.format("%.2f", latest.zScore),
                                valueColor = colorForValue(latest.zScore),
                                labelType = "CALCULATED",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                            StatCard(
                                title = "Signal",
                                value = latest.category.displayName,
                                subtitle = latest.category.description,
                                valueColor = when (latest.category) {
                                    SignalCategory.HIGH_RELATIVE_PREMIUM -> SignalGreen
                                    SignalCategory.HIGH_RELATIVE_DISCOUNT -> SignalRed
                                    SignalCategory.WATCH -> SignalAmber
                                    SignalCategory.LIQUIDITY_WARNING -> SignalAmber
                                    else -> TextSecondary
                                },
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Liquidity",
                                value = String.format("%.0f%%", latest.liquidityScore * 100),
                                subtitle = "Vol: ${latest.volumeA} / ${latest.volumeB}",
                                valueColor = if (latest.liquidityScore >= 0.7) SignalGreen else SignalAmber,
                                labelType = "CALCULATED",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Normalized Prices Chart
                    item {
                        SectionHeader(title = "Normalized Prices", labelType = "CALCULATED · INR/10g @ 999")
                    }

                    item {
                        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceContainerDark) {
                            Column(Modifier.padding(16.dp)) {
                                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(Modifier.size(12.dp, 3.dp), shape = RoundedCornerShape(2.dp), color = ChartLine1) {}
                                        Spacer(Modifier.width(4.dp))
                                        Text(symbolA, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(Modifier.size(12.dp, 3.dp), shape = RoundedCornerShape(2.dp), color = ChartLine2) {}
                                        Spacer(Modifier.width(4.dp))
                                        Text(symbolB, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                LineChart(
                                    data = signals.map { Pair(it.date, it.normalizedPriceA) },
                                    secondaryData = signals.map { Pair(it.date, it.normalizedPriceB) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // Spread Chart
                    item {
                        SectionHeader(title = "Spread (%)", labelType = "CALCULATED")
                    }

                    item {
                        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceContainerDark) {
                            Column(Modifier.padding(16.dp)) {
                                LineChart(
                                    data = signals.map { Pair(it.date, it.spreadPercent) },
                                    lineColor = GoldPrimary,
                                    fillColor = ChartFill1,
                                    zeroLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // Rolling Statistics
                    item {
                        SectionHeader(title = "Rolling Mean & Std Dev", labelType = "CALCULATED · ${settings.rollingWindow}-day window")
                    }

                    item {
                        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceContainerDark) {
                            Column(Modifier.padding(16.dp)) {
                                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                                    StatCard(
                                        title = "Rolling Mean",
                                        value = formatPercent(latest.rollingMean),
                                        labelType = "CALCULATED",
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatCard(
                                        title = "Rolling Std Dev",
                                        value = String.format("%.4f%%", latest.rollingStdDev),
                                        labelType = "CALCULATED",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Z-Score Chart
                    item {
                        SectionHeader(title = "Z-Score", labelType = "CALCULATED")
                    }

                    item {
                        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceContainerDark) {
                            Column(Modifier.padding(16.dp)) {
                                LineChart(
                                    data = signals.map { Pair(it.date, it.zScore) },
                                    lineColor = SignalBlue,
                                    fillColor = ChartFill2,
                                    zeroLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                                    Text("Entry: ±${settings.zScoreEntryThreshold}", style = MaterialTheme.typography.labelSmall, color = SignalAmber)
                                    Text("Exit: ±${settings.zScoreExitThreshold}", style = MaterialTheme.typography.labelSmall, color = SignalGreen)
                                }
                            }
                        }
                    }

                    // Volume & OI Chart
                    item {
                        SectionHeader(title = "Volume", labelType = "DATA")
                    }

                    item {
                        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceContainerDark) {
                            Column(Modifier.padding(16.dp)) {
                                LineChart(
                                    data = signals.map { Pair(it.date, it.volumeA.toDouble()) },
                                    secondaryData = signals.map { Pair(it.date, it.volumeB.toDouble()) },
                                    lineColor = GoldPrimary,
                                    secondaryColor = SignalBlue,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = "Daily Volume"
                                )
                            }
                        }
                    }

                    // Transaction Cost Estimate
                    item {
                        SectionHeader(title = "Estimated Transaction Costs", labelType = "ASSUMPTION")
                    }

                    item {
                        val avgPrice = (latest.normalizedPriceA + latest.normalizedPriceB) / 2
                        val cost = CostEstimator.estimateRoundTrip(avgPrice, settings)

                        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceContainerDark) {
                            Column(Modifier.padding(16.dp), Arrangement.spacedBy(8.dp)) {
                                CostRow("Slippage", "${cost.slippageBps} bps", "ASSUMPTION")
                                CostRow("Brokerage", "${String.format("%.2f", cost.brokerageBps)} bps", "ASSUMPTION")
                                CostRow("Exchange Fee", "${String.format("%.2f", cost.exchangeFeeBps)} bps", "ASSUMPTION")
                                CostRow("GST", "${String.format("%.2f", cost.gstBps)} bps", "ASSUMPTION")
                                HorizontalDivider(color = BorderDefault)
                                CostRow("Round-trip Total", "${String.format("%.2f", cost.totalRoundTripBps)} bps (${formatPercent(cost.totalRoundTripPercent)})", "CALCULATED")
                                HorizontalDivider(color = BorderDefault)
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                    Column {
                                        Text("Net Spread (after costs)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                        MetricLabel("CALCULATED")
                                    }
                                    Text(
                                        formatPercent(latest.netSpreadPercent),
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = colorForValue(latest.netSpreadPercent),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Signal History
                    item {
                        SectionHeader(title = "Recent Signal History", labelType = "CALCULATED")
                    }

                    item {
                        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceContainerDark) {
                            Column(Modifier.padding(12.dp)) {
                                signals.takeLast(15).reversed().forEach { sig ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        Arrangement.SpaceBetween,
                                        Alignment.CenterVertically
                                    ) {
                                        Text(formatDate(sig.date), style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.weight(1f))
                                        Text(formatPercent(sig.spreadPercent), style = MaterialTheme.typography.bodySmall, color = colorForValue(sig.spreadPercent), modifier = Modifier.weight(0.8f))
                                        Text(String.format("%.1f", sig.zScore), style = MaterialTheme.typography.bodySmall, color = colorForValue(sig.zScore), modifier = Modifier.weight(0.6f))
                                        Box(Modifier.weight(1f)) { SignalChip(sig.category) }
                                    }
                                }
                            }
                        }
                    }

                    // Backtest Button
                    item {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.selectPair(symbolA, symbolB)
                                onNavigateToBacktest()
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                        ) {
                            Icon(Icons.Default.Timeline, "", tint = GoldOnPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("Run Backtest on This Pair", color = GoldOnPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CostRow(label: String, value: String, type: String) {
    Row(
        Modifier.fillMaxWidth(),
        Arrangement.SpaceBetween,
        Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            MetricLabel(type)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}
