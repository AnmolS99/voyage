package com.anmol.voyage

import android.app.Application
import com.anmol.voyage.data.CountryDataCache
import com.anmol.voyage.state.DataStoreStateStore

class VoyageApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        CountryDataCache.install(this)
        CountryDataCache.prewarm()
        // Creating the store is cheap — nothing is read until the ViewModel asks,
        // which it does off the main thread.
        DataStoreStateStore.install(this)
    }
}
