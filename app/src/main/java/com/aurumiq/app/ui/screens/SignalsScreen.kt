package com.aurumiq.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aurumiq.app.data.model.Signal
import com.aurumiq.app.data.model.SignalCategory
import com.aurumiq.app.ui.components.*
import com.aurumiq.app.ui.theme.*
import com.aurumiq.app.ui.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onPairSelected: (String, String) -> Unit
) {
    val signalsState by viewModel.signalsState.collectAsState()
    val dataSource by viewModel.dataSource.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadAllSignals()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Relative Value Monitor", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                        Text("All contract pairs", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = TextSecondary)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadAllSignals() }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = TextSecondary)
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                DataSourceBadge(dataSource, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }

            when (val state = signalsState) {
                is SignalsState.Loading -> {
                    item {
                        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = GoldPrimary)
                        }
                    }
                }

                is SignalsState.Error -> {
                    item {
                        Surface(shape = RoundedCornerShape(12.dp), color = SignalRed.copy(alpha = 0.1f)) {
                            Text(state.message, Modifier.padding(16.dp), color = SignalRed)
                        }
                    }
                }

                is SignalsState.Empty -> {
                    item {
                        Text("No signals generated yet.", color = TextMuted)
                    }
                }

                is SignalsState.Loaded -> {
                    // Signal summary stats
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val premiums = state.signals.count { it.category == SignalCategory.HIGH_RELATIVE_PREMIUM }
                            val discounts = state.signals.count { it.category == SignalCategory.HIGH_RELATIVE_DISCOUNT }
                            val watches = state.signals.count { it.category == SignalCategory.WATCH }

                            StatCard(
                                title = "Premiums",
                                value = premiums.toString(),
                                valueColor = SignalGreen,
                                labelType = "CALCULATED",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Discounts",
                                value = discounts.toString(),
                                valueColor = SignalRed,
                                labelType = "CALCULATED",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Watch",
                                value = watches.toString(),
                                valueColor = SignalAmber,
                                labelType = "CALCULATED",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Full signal table
                    item {
                        SectionHeader(title = "All Pairs — Latest Signals", labelType = "CALCULATED METRIC")
                    }

                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceContainerDark
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // Header
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Pair", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.weight(1.3f))
                                    Text("NormA", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.weight(0.9f))
                                    Text("NormB", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.weight(0.9f))
                                    Text("Spread%", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.weight(0.8f))
                                    Text("Z", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.weight(0.6f))
                                    Text("Signal", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.weight(1f))
                                }
                                HorizontalDivider(color = BorderDefault)

                                state.signals.sortedByDescending { kotlin.math.abs(it.zScore) }.forEach { signal ->
                                    FullSignalRow(
                                        signal = signal,
                                        onClick = { onPairSelected(signal.symbolA, signal.symbolB) }
                                    )
                                    HorizontalDivider(color = BorderMuted)
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
private fun FullSignalRow(signal: Signal, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1.3f)) {
            Text(
                "${signal.symbolA}",
                style = MaterialTheme.typography.bodySmall,
                color = GoldPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "vs ${signal.symbolB}",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        Text(
            formatPrice(signal.normalizedPriceA),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.weight(0.9f)
        )
        Text(
            formatPrice(signal.normalizedPriceB),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.weight(0.9f)
        )
        Text(
            formatPercent(signal.spreadPercent),
            style = MaterialTheme.typography.bodySmall,
            color = colorForValue(signal.spreadPercent),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.8f)
        )
        Text(
            String.format("%.1f", signal.zScore),
            style = MaterialTheme.typography.bodySmall,
            color = colorForValue(signal.zScore),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.6f)
        )
        Box(modifier = Modifier.weight(1f)) {
            SignalChip(signal.category)
        }
    }
}
