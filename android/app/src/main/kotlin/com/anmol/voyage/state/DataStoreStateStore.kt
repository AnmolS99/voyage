package com.anmol.voyage.state

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException

/**
 * The real [StateStore]: one JSON document in Jetpack DataStore.
 *
 * DataStore rather than `SharedPreferences` because writes are transactional and
 * happen off the main thread — the app never blocks UI on a save, and a kill
 * mid-write leaves the previous document intact rather than a half-written one.
 * There is exactly one instance per process because DataStore requires it: two
 * stores over the same file would race each other.
 *
 * The file lives in `files/datastore/`, which the backup rules include, so state
 * follows the user to a new device.
 */
class DataStoreStateStore private constructor(
    private val dataStore: DataStore<PersistedState>,
) : StateStore {

    override suspend fun load(): PersistedState = dataStore.data
        // Corruption is already handled by replacing the file; this is for the
        // rarer read failures (a file we cannot open at all), where starting
        // empty beats refusing to launch.
        .catch { cause -> if (cause is IOException) emit(PersistedState()) else throw cause }
        .first()

    override suspend fun save(state: PersistedState) {
        dataStore.updateData { state }
    }

    companion object {
        /** Named for what it holds rather than how, since the format may change. */
        private const val FILE_NAME = "voyage_state.json"

        @Volatile
        private var instance: DataStoreStateStore? = null

        /** The process-wide store. [install] runs first, from `VoyageApplication`. */
        val shared: DataStoreStateStore
            get() = checkNotNull(instance) { "DataStoreStateStore.install(context) has not run" }

        @Synchronized
        fun install(context: Context) {
            if (instance != null) return
            val appContext = context.applicationContext
            instance = DataStoreStateStore(
                DataStoreFactory.create(
                    serializer = PersistedStateSerializer,
                    // An unreadable document is worth less than a working app:
                    // start over rather than throw on every read.
                    corruptionHandler = ReplaceFileCorruptionHandler { PersistedState() },
                    produceFile = { appContext.dataStoreFile(FILE_NAME) },
                ),
            )
        }
    }
}

/** Bridges DataStore to [PersistedStateCodec]. */
private object PersistedStateSerializer : Serializer<PersistedState> {

    override val defaultValue = PersistedState()

    override suspend fun readFrom(input: InputStream): PersistedState = try {
        PersistedStateCodec.decode(input)
    } catch (cause: SerializationException) {
        // Signals the corruption handler above; anything else propagates.
        throw CorruptionException("Cannot read saved Voyage state", cause)
    }

    override suspend fun writeTo(t: PersistedState, output: OutputStream) {
        PersistedStateCodec.encode(t, output)
    }
}
