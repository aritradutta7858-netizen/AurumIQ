package com.aurumiq.app.ui.navigation

/**
 * Navigation routes for the AurumIQ app.
 */
sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Signals : Screen("signals")
    object PairDetail : Screen("pair_detail/{symbolA}/{symbolB}") {
        fun createRoute(symbolA: String, symbolB: String) = "pair_detail/$symbolA/$symbolB"
    }
    object Backtest : Screen("backtest")
    object Methodology : Screen("methodology")
}
