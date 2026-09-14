package com.anmol.voyage.globe

import com.anmol.voyage.data.SharedFiles
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test

/**
 * `world_meshes.bin` loads back as exactly the meshes triangulation builds.
 *
 * The app never triangulates — it reads what the build wrote — so the tests of
 * the triangulator (`GlobeGeometryWorldTest` and the rest) only speak for what
 * the app draws through this round trip. Compared bit for bit: the file is a
 * cache, not an approximation.
 */
class WorldMeshesFileTest {

    companion object {
        private lateinit var triangulated: WorldMeshes
        private lateinit var loaded: WorldMeshes

        @BeforeClass
        @JvmStatic
        fun roundTrip() {
            val countries = SharedFiles.parseCountries()
            triangulated = WorldMeshes.triangulate(countries)
            val bytes = ByteArrayOutputStream().also { WorldMeshesFile.write(countries, it) }.toByteArray()
            loaded = WorldMeshesFile.read(bytes.inputStream())
        }
    }

    @Test
    fun `country fills load back bit for bit`() {
        assertEquals(triangulated.countries.map { it.name }, loaded.countries.map { it.name })
        triangulated.countries.zip(loaded.countries) { want, got ->
            assertArrayEquals(want.name, want.mesh.positions, got.mesh.positions, 0f)
            assertArrayEquals(want.name, want.mesh.uvs, got.mesh.uvs, 0f)
            assertArrayEquals(want.name, want.mesh.indices, got.mesh.indices)
            assertEquals(want.name, want.mesh.gridFallbackRingCount, got.mesh.gridFallbackRingCount)
        }
    }

    @Test
    fun `outline sectors load back bit for bit`() {
        assertEquals(triangulated.outlineSectors.size, loaded.outlineSectors.size)
        triangulated.outlineSectors.zip(loaded.outlineSectors) { want, got ->
            assertArrayEquals(want.positions, got.positions, 0f)
            assertArrayEquals(want.miters, got.miters, 0f)
            assertArrayEquals(want.indices, got.indices)
            // The renderer culls by these, so a drift would hide borders.
            assertEquals(want.center, got.center)
            assertEquals(want.boundingRadius, got.boundingRadius, 0f)
        }
    }
}
