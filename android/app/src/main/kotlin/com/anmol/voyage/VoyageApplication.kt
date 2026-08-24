package com.anmol.voyage

import android.app.Application
import com.anmol.voyage.data.CountryDataCache
import com.anmol.voyage.globe.GlobeGeometryCache
import com.anmol.voyage.state.DataStoreStateStore

class VoyageApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        CountryDataCache.install(this)
        CountryDataCache.prewarm()
        // Triangulating the globe queues behind parsing on the countries lazy,
        // so by the time Home is interactive the geometry is usually already
        // built — and it is never built twice.
        GlobeGeometryCache.prewarm()
        // Creating the store is cheap — nothing is read until the ViewModel asks,
        // which it does off the main thread.
        DataStoreStateStore.install(this)
    }
}
