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
import com.aurumiq.app.data.model.Settings
import com.aurumiq.app.ui.components.*
import com.aurumiq.app.ui.theme.*
import com.aurumiq.app.ui.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacktestScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val backtestState by viewModel.backtestState.collectAsState()
    val selectedPair by viewModel.selectedPair.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val dataSource by viewModel.dataSource.collectAsState()

    var zScoreThreshold by remember { mutableStateOf(settings.zScoreEntryThreshold.toFloat()) }
    var slippageBps by remember { mutableStateOf(settings.slippageBps.toFloat()) }
    var brokeragePerLot by remember { mutableStateOf(settings.brokeragePerLot.toFloat()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Walk-Forward Backtest", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                        Text("${selectedPair.first} vs ${selectedPair.second}", style = MaterialTheme.typography.bodySmall, color = GoldPrimary)
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

            // Configuration
            item {
                SectionHeader(title = "Configuration", labelType = "ASSUMPTION")
            }

            item {
                Surface(shape = RoundedCornerShape(12.dp), color = SurfaceContainerDark) {
                    Column(Modifier.padding(16.dp), Arrangement.spacedBy(16.dp)) {
                        // Z-Score Threshold
                        Column {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Z-Score Entry Threshold", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                Text("±${String.format("%.1f", zScoreThreshold)}", style = MaterialTheme.typography.bodyMedium, color = GoldPrimary, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = zScoreThreshold,
                                onValueChange = { zScoreThreshold = it },
                                valueRange = 1.0f..4.0f,
                                steps = 5,
                                colors = SliderDefaults.colors(
                                    thumbColor = GoldPrimary,
                                    activeTrackColor = GoldPrimary
                                )
                            )
                        }

                        // Slippage
                        Column {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Slippage", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                Text("${String.format("%.0f", slippageBps)} bps", style = MaterialTheme.typography.bodyMedium, color = GoldPrimary, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = slippageBps,
                                onValueChange = { slippageBps = it },
                                valueRange = 0f..20f,
                                steps = 19,
                                colors = SliderDefaults.colors(thumbColor = GoldPrimary, activeTrackColor = GoldPrimary)
                            )
                        }

                        // Brokerage
                        Column {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Brokerage per lot", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                Text("₹${String.format("%.0f", brokeragePerLot)}", style = MaterialTheme.typography.bodyMedium, color = GoldPrimary, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = brokeragePerLot,
                                onValueChange = { brokeragePerLot = it },
                                valueRange = 0f..100f,
                                steps = 9,
                                colors = SliderDefaults.colors(thumbColor = GoldPrimary, activeTrackColor = GoldPrimary)
                            )
                        }

                        // Info
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SignalBlue.copy(alpha = 0.1f)
                        ) {
                            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Info, "", tint = SignalBlue, modifier = Modifier.size(16.dp))
                                Text(
                                    "Walk-forward: 60% training / 40% test split. " +
                                    "NO look-ahead bias — signals at date T use only data ≤ T. " +
                                    "Execution assumed at next-day open.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SignalBlue
                                )
                            }
                        }

                        // Run Button
                        Button(
                            onClick = {
                                viewModel.updateSettings(
                                    settings.copy(
                                        zScoreEntryThreshold = zScoreThreshold.toDouble(),
                                        slippageBps = slippageBps.toDouble(),
                                        brokeragePerLot = brokeragePerLot.toDouble()
                                    )
                                )
                                viewModel.runBacktest()
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            enabled = backtestState !is BacktestState.Running
                        ) {
                            if (backtestState is BacktestState.Running) {
                                CircularProgressIndicator(Modifier.size(20.dp), color = GoldOnPrimary, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Running...", color = GoldOnPrimary)
                            } else {
                                Icon(Icons.Default.PlayArrow, "", tint = GoldOnPrimary)
                                Spacer(Modifier.width(8.dp))
                                Text("Run Backtest", color = GoldOnPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Results
            when (val state = backtestState) {
                is BacktestState.Running -> {
                    item {
                        Box(Modifier.fillMaxWidth().height(100.dp), Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = GoldPrimary)
                                Spacer(Modifier.height(8.dp))
                                Text("Running walk-forward backtest...", color = TextSecondary)
                            }
                        }
                    }
                }

                is BacktestState.Error -> {
                    item {
                        Surface(shape = RoundedCornerShape(12.dp), color = SignalRed.copy(0.1f)) {
                            Text(state.message, Modifier.padding(16.dp), color = SignalRed)
                        }
                    }
                }

                is BacktestState.Complete -> {
                    val result = state.result

                    item {
                        SectionHeader(title = "Backtest Results", labelType = "BACKTEST RESULT")
                    }

                    // Performance Overview
                    item {
                        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                            StatCard(
                                title = "Initial Capital",
                                value = formatCurrency(result.initialCapital),
                                labelType = "ASSUMPTION",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Final Capital",
                                value = formatCurrency(result.finalCapital),
                                valueColor = colorForValue(result.netPnL),
                                labelType = "BACKTEST",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                            StatCard(
                                title = "Gross P&L",
                                value = formatCurrency(result.grossPnL),
                                valueColor = colorForValue(result.grossPnL),
                                labelType = "BACKTEST",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Total Costs",
                                value = formatCurrency(result.totalCosts),
                                valueColor = SignalRed,
                                labelType = "CALCULATED",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                            StatCard(
                                title = "Net P&L",
                                value = formatCurrency(result.netPnL),
                                valueColor = colorForValue(result.netPnL),
                                labelType = "BACKTEST",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Return",
                                value = formatPercent(result.strategyReturnPercent),
                                valueColor = colorForValue(result.strategyReturnPercent),
                                labelType = "BACKTEST",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Trade Statistics
                    item {
                        SectionHeader(title = "Trade Statistics", labelType = "BACKTEST RESULT")
                    }

                    item {
                        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                            StatCard(
                                title = "Trade Count",
                                value = result.tradeCount.toString(),
                                labelType = "BACKTEST",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Win Rate",
                                value = "${String.format("%.1f", result.winRate * 100)}%",
                                subtitle = "${result.winCount}W / ${result.lossCount}L",
                                valueColor = if (result.winRate > 0.5) SignalGreen else SignalRed,
                                labelType = "BACKTEST",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                            StatCard(
                                title = "Max Drawdown",
                                value = formatPercent(result.maxDrawdownPercent),
                                valueColor = SignalRed,
                                labelType = "BACKTEST",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Avg Trade",
                                value = formatCurrency(result.averageTrade),
                                valueColor = colorForValue(result.averageTrade),
                                labelType = "BACKTEST",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Strategy vs Gold
                    item {
                        SectionHeader(title = "Strategy vs Underlying Gold", labelType = "BACKTEST RESULT")
                    }

                    item {
                        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceContainerDark) {
                            Column(Modifier.padding(16.dp), Arrangement.spacedBy(12.dp)) {
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    Column {
                                        Text("Gold Price Return", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                        MetricLabel("DATA")
                                    }
                                    Text(
                                        formatPercent(result.goldReturnPercent),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colorForValue(result.goldReturnPercent),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    Column {
                                        Text("Strategy Return", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                        MetricLabel("BACKTEST")
                                    }
                                    Text(
                                        formatPercent(result.strategyReturnPercent),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colorForValue(result.strategyReturnPercent),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                HorizontalDivider(color = BorderDefault)
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    Column {
                                        Text("Excess Return", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                        MetricLabel("CALCULATED")
                                    }
                                    Text(
                                        formatPercent(result.excessReturnPercent),
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = colorForValue(result.excessReturnPercent),
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Surface(shape = RoundedCornerShape(8.dp), color = SignalBlue.copy(alpha = 0.1f)) {
                                    Text(
                                        "Excess return isolates the strategy's alpha from directional gold exposure. " +
                                        "A market-neutral spread strategy should show excess return independent of gold's direction.",
                                        Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SignalBlue
                                    )
                                }
                            }
                        }
                    }

                    // Equity Curve
                    if (result.equityCurve.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Equity Curve", labelType = "BACKTEST RESULT")
                        }

                        item {
                            Surface(shape = RoundedCornerShape(12.dp), color = SurfaceContainerDark) {
                                Column(Modifier.padding(16.dp)) {
                                    LineChart(
                                        data = result.equityCurve.map { Pair(it.date, it.equity) },
                                        lineColor = GoldPrimary,
                                        fillColor = ChartFill1,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    // Drawdown
                    if (result.equityCurve.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Drawdown", labelType = "BACKTEST RESULT")
                        }

                        item {
                            Surface(shape = RoundedCornerShape(12.dp), color = SurfaceContainerDark) {
                                Column(Modifier.padding(16.dp)) {
                                    LineChart(
                                        data = result.equityCurve.map { Pair(it.date, -it.drawdown) },
                                        lineColor = SignalRed,
                                        fillColor = SignalRed.copy(alpha = 0.15f),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    // Trade List
                    if (result.trades.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Trade Log (${result.trades.size} trades)", labelType = "BACKTEST RESULT")
                        }

                        item {
                            Surface(shape = RoundedCornerShape(12.dp), color = SurfaceContainerDark) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                        Text("Entry", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.weight(1f))
                                        Text("Exit", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.weight(1f))
                                        Text("Dir", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.weight(0.8f))
                                        Text("Net P&L", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.weight(0.8f))
                                        Text("Reason", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.weight(1f))
                                    }
                                    HorizontalDivider(color = BorderDefault)

                                    result.trades.forEach { trade ->
                                        Row(
                                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(formatDate(trade.entryDate), style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
                                            Text(formatDate(trade.exitDate), style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
                                            Text(
                                                if (trade.direction.name.contains("LONG_A")) "L/S" else "S/L",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary,
                                                modifier = Modifier.weight(0.8f)
                                            )
                                            Text(
                                                formatCurrency(trade.netPnL),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colorForValue(trade.netPnL),
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.weight(0.8f)
                                            )
                                            Text(trade.exitReason.displayName, style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Disclaimers
                    item {
                        Surface(shape = RoundedCornerShape(8.dp), color = SignalAmber.copy(alpha = 0.1f)) {
                            Column(Modifier.padding(12.dp), Arrangement.spacedBy(4.dp)) {
                                Text("⚠ Important Disclaimers", style = MaterialTheme.typography.labelMedium, color = SignalAmber, fontWeight = FontWeight.Bold)
                                Text("• This backtest uses ${if (result.dataSource == com.aurumiq.app.data.model.DataSource.DEMO) "SYNTHETIC DEMO" else "IMPORTED"} data", style = MaterialTheme.typography.bodySmall, color = SignalAmber)
                                Text("• Past performance does not guarantee future results", style = MaterialTheme.typography.bodySmall, color = SignalAmber)
                                Text("• Slippage and execution assumptions are estimates", style = MaterialTheme.typography.bodySmall, color = SignalAmber)
                                Text("• This is a research tool, not trading advice", style = MaterialTheme.typography.bodySmall, color = SignalAmber)
                            }
                        }
                    }
                }

                else -> { /* Empty state — waiting for user to run */ }
            }
        }
    }
}
