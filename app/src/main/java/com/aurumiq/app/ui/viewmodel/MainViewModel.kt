package com.aurumiq.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aurumiq.app.AurumIQApplication
import com.aurumiq.app.data.model.*
import com.aurumiq.app.data.repository.MarketRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.InputStream

/**
 * Main ViewModel for the AurumIQ application.
 * Manages all UI state following the MVVM pattern.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = (application as AurumIQApplication).database
    private val repository = MarketRepository(database.marketRecordDao())

    // ── Settings ──────────────────────────────────────────────
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    // ── Dashboard State ───────────────────────────────────────
    private val _dashboardState = MutableStateFlow<DashboardState>(DashboardState.Loading)
    val dashboardState: StateFlow<DashboardState> = _dashboardState.asStateFlow()

    // ── Signals State ─────────────────────────────────────────
    private val _signalsState = MutableStateFlow<SignalsState>(SignalsState.Empty)
    val signalsState: StateFlow<SignalsState> = _signalsState.asStateFlow()

    // ── Pair Detail State ─────────────────────────────────────
    private val _pairDetailState = MutableStateFlow<PairDetailState>(PairDetailState.Empty)
    val pairDetailState: StateFlow<PairDetailState> = _pairDetailState.asStateFlow()

    // ── Backtest State ────────────────────────────────────────
    private val _backtestState = MutableStateFlow<BacktestState>(BacktestState.Empty)
    val backtestState: StateFlow<BacktestState> = _backtestState.asStateFlow()

    // ── Import State ──────────────────────────────────────────
    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    // ── Record Count ──────────────────────────────────────────
    val recordCount: StateFlow<Int> = repository.getRecordCount()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    // ── Data Source ───────────────────────────────────────────
    private val _dataSource = MutableStateFlow(DataSource.DEMO)
    val dataSource: StateFlow<DataSource> = _dataSource.asStateFlow()

    // ── Selected Pair ─────────────────────────────────────────
    private val _selectedPair = MutableStateFlow(Pair("GOLDM", "GOLDTEN"))
    val selectedPair: StateFlow<Pair<String, String>> = _selectedPair.asStateFlow()

    init {
        loadDemoDataIfEmpty()
    }

    private fun loadDemoDataIfEmpty() {
        viewModelScope.launch {
            val count = recordCount.first { true }
            if (count == 0) {
                loadDemoData()
            } else {
                refreshDashboard()
            }
        }
    }

    fun loadDemoData() {
        viewModelScope.launch {
            _dashboardState.value = DashboardState.Loading
            try {
                val count = repository.loadDemoData()
                _dataSource.value = DataSource.DEMO
                refreshDashboard()
            } catch (e: Exception) {
                _dashboardState.value = DashboardState.Error("Failed to load demo data: ${e.message}")
            }
        }
    }

    fun importCsv(inputStream: InputStream) {
        viewModelScope.launch {
            _importState.value = ImportState.Importing
            try {
                val result = repository.importCsv(inputStream)
                _importState.value = ImportState.Complete(result)
                _dataSource.value = DataSource.IMPORTED
                refreshDashboard()
            } catch (e: Exception) {
                _importState.value = ImportState.Error("Import failed: ${e.message}")
            }
        }
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            _dashboardState.value = DashboardState.Loading
            try {
                val summary = repository.getDashboardSummary(_settings.value)
                _dataSource.value = summary.dataSource
                _dashboardState.value = DashboardState.Loaded(summary)
            } catch (e: Exception) {
                _dashboardState.value = DashboardState.Error("Dashboard error: ${e.message}")
            }
        }
    }

    fun loadAllSignals() {
        viewModelScope.launch {
            _signalsState.value = SignalsState.Loading
            try {
                val pairs = listOf(
                    Pair("GOLDM", "GOLDTEN"),
                    Pair("GOLDM", "GOLDGUINEA"),
                    Pair("GOLDM", "GOLDPETAL"),
                    Pair("GOLDTEN", "GOLDGUINEA"),
                    Pair("GOLDTEN", "GOLDPETAL"),
                    Pair("GOLDGUINEA", "GOLDPETAL")
                )

                val allSignals = pairs.flatMap { (a, b) ->
                    try {
                        val signals = repository.generateSignals(a, b, _settings.value)
                        signals.takeLast(1) // Latest signal per pair
                    } catch (e: Exception) {
                        emptyList()
                    }
                }

                _signalsState.value = SignalsState.Loaded(allSignals)
            } catch (e: Exception) {
                _signalsState.value = SignalsState.Error("Signal generation error: ${e.message}")
            }
        }
    }

    fun selectPair(symbolA: String, symbolB: String) {
        _selectedPair.value = Pair(symbolA, symbolB)
        loadPairDetail(symbolA, symbolB)
    }

    fun loadPairDetail(symbolA: String, symbolB: String) {
        viewModelScope.launch {
            _pairDetailState.value = PairDetailState.Loading
            try {
                val signals = repository.generateSignals(symbolA, symbolB, _settings.value)
                _pairDetailState.value = PairDetailState.Loaded(symbolA, symbolB, signals)
            } catch (e: Exception) {
                _pairDetailState.value = PairDetailState.Error("Detail error: ${e.message}")
            }
        }
    }

    fun runBacktest() {
        viewModelScope.launch {
            _backtestState.value = BacktestState.Running
            try {
                val (symbolA, symbolB) = _selectedPair.value
                val dateRange = repository.getDateRange()
                if (dateRange == null) {
                    _backtestState.value = BacktestState.Error("No data available")
                    return@launch
                }

                val totalDays = (dateRange.second - dateRange.first) / (24 * 60 * 60 * 1000)
                val trainingEnd = dateRange.first + (totalDays * 24 * 60 * 60 * 1000 * 60 / 100)
                val testStart = trainingEnd + (24 * 60 * 60 * 1000) // Next day

                val result = repository.runBacktest(
                    symbolA = symbolA,
                    symbolB = symbolB,
                    settings = _settings.value,
                    trainingStart = dateRange.first,
                    trainingEnd = trainingEnd,
                    testStart = testStart,
                    testEnd = dateRange.second
                )

                _backtestState.value = BacktestState.Complete(result)
            } catch (e: Exception) {
                _backtestState.value = BacktestState.Error("Backtest error: ${e.message}")
            }
        }
    }

    fun updateSettings(newSettings: Settings) {
        _settings.value = newSettings
    }

    fun clearData() {
        viewModelScope.launch {
            repository.clearAllData()
            _dashboardState.value = DashboardState.Loading
            _signalsState.value = SignalsState.Empty
            _pairDetailState.value = PairDetailState.Empty
            _backtestState.value = BacktestState.Empty
        }
    }
}

// ── UI State Models ──────────────────────────────────────────

sealed class DashboardState {
    object Loading : DashboardState()
    data class Loaded(val summary: MarketRepository.DashboardSummary) : DashboardState()
    data class Error(val message: String) : DashboardState()
}

sealed class SignalsState {
    object Empty : SignalsState()
    object Loading : SignalsState()
    data class Loaded(val signals: List<Signal>) : SignalsState()
    data class Error(val message: String) : SignalsState()
}

sealed class PairDetailState {
    object Empty : PairDetailState()
    object Loading : PairDetailState()
    data class Loaded(
        val symbolA: String,
        val symbolB: String,
        val signals: List<Signal>
    ) : PairDetailState()
    data class Error(val message: String) : PairDetailState()
}

sealed class BacktestState {
    object Empty : BacktestState()
    object Running : BacktestState()
    data class Complete(val result: BacktestResult) : BacktestState()
    data class Error(val message: String) : BacktestState()
}

sealed class ImportState {
    object Idle : ImportState()
    object Importing : ImportState()
    data class Complete(val result: com.aurumiq.app.data.parser.CsvParser.ParseResult) : ImportState()
    data class Error(val message: String) : ImportState()
}
