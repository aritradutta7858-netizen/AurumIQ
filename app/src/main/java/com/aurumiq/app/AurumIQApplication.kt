package com.aurumiq.app

import android.app.Application
import com.aurumiq.app.data.local.AurumDatabase

/**
 * Application class for AurumIQ — Commodity Derivatives Intelligence.
 * Initializes the Room database as a singleton.
 */
class AurumIQApplication : Application() {

    val database: AurumDatabase by lazy {
        AurumDatabase.getInstance(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: AurumIQApplication
            private set
    }
}
