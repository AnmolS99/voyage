package com.anmol.voyage

import android.app.Application
import com.anmol.voyage.data.CountryDataCache

class VoyageApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        CountryDataCache.install(this)
        CountryDataCache.prewarm()
    }
}
