package com.aurumiq.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aurumiq.app.data.model.ContractSpec
import com.aurumiq.app.data.model.SignalCategory
import com.aurumiq.app.ui.components.*
import com.aurumiq.app.ui.theme.*
import com.aurumiq.app.ui.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToSignals: () -> Unit,
    onNavigateToPairDetail: (String, String) -> Unit,
    onNavigateToBacktest: () -> Unit,
    onNavigateToMethodology: () -> Unit,
    onImportCsv: () -> Unit
) {
    val dashboardState by viewModel.dashboardState.collectAsState()
    val dataSource by viewModel.dataSource.collectAsState()
    val recordCount by viewModel.recordCount.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "AurumIQ",
                            style = MaterialTheme.typography.headlineMedium,
                            color = GoldPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Commodity Derivatives Intelligence",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onImportCsv) {
                        Icon(Icons.Default.FileOpen, "Import CSV", tint = TextSecondary)
                    }
                    IconButton(onClick = { viewModel.refreshDashboard() }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark
                )
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
            // Data Source Banner
            item {
                DataSourceBadge(
                    dataSource = dataSource,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            when (val state = dashboardState) {
                is DashboardState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = GoldPrimary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Loading data...", color = TextSecondary)
                            }
                        }
                    }
                }

                is DashboardState.Error -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SignalRed.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = state.message,
                                modifier = Modifier.padding(16.dp),
                                color = SignalRed
                            )
                        }
                    }
                }

                is DashboardState.Loaded -> {
                    val summary = state.summary

                    // Stats Cards Row 1
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard(
                                title = "Active Contracts",
                                value = summary.activeSymbols.size.toString(),
                                subtitle = summary.activeSymbols.joinToString(", "),
                                labelType = "DATA",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Total Records",
                                value = "%,d".format(summary.totalRecords),
                                subtitle = if (summary.dateRange != null)
                                    "${formatDate(summary.dateRange.first)} — ${formatDate(summary.dateRange.second)}"
                                else "No data",
                                labelType = "DATA",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Stats Cards Row 2 — Signal summary
                    item {
                        val latestSignals = summary.latestSignals
                        val premiumSignal = latestSignals.filter {
                            it.category == SignalCategory.HIGH_RELATIVE_PREMIUM
                        }.maxByOrNull { it.spreadPercent }
                        val discountSignal = latestSignals.filter {
                            it.category == SignalCategory.HIGH_RELATIVE_DISCOUNT
                        }.minByOrNull { it.spreadPercent }
                        val watchCount = latestSignals.count {
                            it.category == SignalCategory.WATCH ||
                            it.category == SignalCategory.HIGH_RELATIVE_PREMIUM ||
                            it.category == SignalCategory.HIGH_RELATIVE_DISCOUNT
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard(
                                title = "Largest Premium",
                                value = if (premiumSignal != null) formatPercent(premiumSignal.spreadPercent) else "—",
                                subtitle = if (premiumSignal != null) "${premiumSignal.symbolA} vs ${premiumSignal.symbolB}" else "None detected",
                                valueColor = SignalGreen,
                                labelType = "CALCULATED",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Largest Discount",
                                value = if (discountSignal != null) formatPercent(discountSignal.spreadPercent) else "—",
                                subtitle = if (discountSignal != null) "${discountSignal.symbolA} vs ${discountSignal.symbolB}" else "None detected",
                                valueColor = SignalRed,
                                labelType = "CALCULATED",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Watch Signals & Data Quality
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val watchSignals = summary.latestSignals.count {
                                it.category != SignalCategory.NORMAL && it.category != SignalCategory.INSUFFICIENT_DATA
                            }
                            StatCard(
                                title = "Watch Signals",
                                value = watchSignals.toString(),
                                subtitle = "Active alerts (30d)",
                                valueColor = if (watchSignals > 0) SignalAmber else TextSecondary,
                                labelType = "CALCULATED",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Data Quality",
                                value = "${summary.dataQuality.symbolCount} symbols",
                                subtitle = "~${String.format("%.1f", summary.dataQuality.avgRecordsPerDay)} rec/day",
                                labelType = "DATA",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Relative Value Table Header
                    item {
                        SectionHeader(
                            title = "Relative Value Monitor",
                            labelType = "CALCULATED METRIC"
                        )
                    }

                    // Latest signals as a table
                    item {
                        val latestSignals = summary.latestSignals
                        if (latestSignals.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SurfaceContainerDark
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Outlined.Assessment, "", tint = TextMuted, modifier = Modifier.size(48.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("No signals yet. Loading data...", color = TextMuted)
                                }
                            }
                        } else {
                            // Show latest signals grouped
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SurfaceContainerDark
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    // Table header
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Pair", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.weight(1.5f))
                                        Text("Spread", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                        Text("Z-Score", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.weight(1f))
                                        Text("Signal", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.weight(1.2f))
                                    }
                                    HorizontalDivider(color = BorderDefault)

                                    latestSignals.takeLast(10).forEach { signal ->
                                        SignalRow(
                                            signal = signal,
                                            onClick = {
                                                onNavigateToPairDetail(signal.symbolA, signal.symbolB)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Quick Navigation
                    item {
                        SectionHeader(title = "Analysis Tools")
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            NavigationCard(
                                icon = Icons.Default.CompareArrows,
                                title = "All Signals",
                                subtitle = "Full relative value table",
                                onClick = onNavigateToSignals,
                                modifier = Modifier.weight(1f)
                            )
                            NavigationCard(
                                icon = Icons.Default.Timeline,
                                title = "Backtest",
                                subtitle = "Walk-forward testing",
                                onClick = onNavigateToBacktest,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            NavigationCard(
                                icon = Icons.Default.School,
                                title = "Methodology",
                                subtitle = "How it works",
                                onClick = onNavigateToMethodology,
                                modifier = Modifier.weight(1f)
                            )
                            NavigationCard(
                                icon = Icons.Default.FileOpen,
                                title = "Import Data",
                                subtitle = "Load MCX CSV",
                                onClick = onImportCsv,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Contract Specs
                    item {
                        SectionHeader(title = "Contract Specifications", labelType = "REFERENCE")
                    }

                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceContainerDark
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Contract", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.weight(1.2f))
                                    Text("Lot", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.weight(0.8f))
                                    Text("Quote", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.weight(0.8f))
                                    Text("Purity", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.weight(0.8f))
                                }
                                HorizontalDivider(color = BorderDefault)
                                ContractSpec.ALL.forEach { spec ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1.2f)) {
                                            Text(spec.symbol, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                            Text(spec.displayName, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                        }
                                        Text("${spec.lotSizeGrams.toInt()}g", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(0.8f))
                                        Text("per ${spec.quotationBasisGrams.toInt()}g", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(0.8f))
                                        Text("${spec.purityPartsPerThousand}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(0.8f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalRow(
    signal: com.aurumiq.app.data.model.Signal,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1.5f)) {
            Text(
                "${signal.symbolA} / ${signal.symbolB}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                formatDate(signal.date),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        Text(
            formatPercent(signal.spreadPercent),
            style = MaterialTheme.typography.bodyMedium,
            color = colorForValue(signal.spreadPercent),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(
            String.format("%.2f", signal.zScore),
            style = MaterialTheme.typography.bodyMedium,
            color = colorForValue(signal.zScore),
            modifier = Modifier.weight(1f)
        )
        Box(modifier = Modifier.weight(1.2f)) {
            SignalChip(signal.category)
        }
    }
}

@Composable
private fun NavigationCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = SurfaceContainerDark
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, "", tint = GoldPrimary, modifier = Modifier.size(28.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
    }
}
