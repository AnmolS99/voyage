package com.anmol.voyage

import android.app.Application
import com.anmol.voyage.data.CountryDataCache
import com.anmol.voyage.data.EarthTextureCache
import com.anmol.voyage.globe.GlobeGeometryCache
import com.anmol.voyage.state.DataStoreStateStore

class VoyageApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        CountryDataCache.install(this)
        CountryDataCache.prewarm()
        // Loading the globe queues behind the countries on their lazy, so by the
        // time Home is interactive the geometry is usually already built — and
        // it is never built twice. Both read what the build generated from
        // world.geojson instead of parsing and triangulating it here.
        GlobeGeometryCache.install(this)
        GlobeGeometryCache.prewarm()
        // Not prewarmed: which style to decode is only known once the saved state
        // has loaded, so Home asks for it then.
        EarthTextureCache.install(this)
        // Creating the store is cheap — nothing is read until the ViewModel asks,
        // which it does off the main thread.
        DataStoreStateStore.install(this)
    }
}
