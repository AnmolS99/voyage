package com.anmol.voyage.state

/**
 * Where [VoyageState] reads and writes its [PersistedState].
 *
 * An interface rather than a direct DataStore call so the state machine can be
 * exercised on the JVM, with no device and no files, and so a future sync layer
 * can slot in behind it.
 */
interface StateStore {

    /** The saved document, or a default one if nothing has been saved yet. */
    suspend fun load(): PersistedState

    /** Replaces the saved document with [state]. */
    suspend fun save(state: PersistedState)
}

/**
 * A [StateStore] that forgets everything when the process does.
 *
 * It is the default for [VoyageState], which keeps throwaway instances — tests,
 * Compose previews — from touching the real one, the same role `inMemory: true`
 * plays for the iOS `GlobeState`.
 */
class InMemoryStateStore(initial: PersistedState = PersistedState()) : StateStore {

    var state: PersistedState = initial
        private set

    /** How many times [save] has been called; assertions in tests read it. */
    var saveCount: Int = 0
        private set

    override suspend fun load(): PersistedState = state

    override suspend fun save(state: PersistedState) {
        this.state = state
        saveCount++
    }
}
