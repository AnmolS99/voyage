package com.anmol.voyage.globe

import com.anmol.voyage.data.SharedFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GlobeMaterialSourcesTest {

    @Test
    fun `the outline shader looks for the tag the ring mesh writes`() {
        val source = SharedFiles.file("android/app/src/main/materials/outline.mat").readText()
        val declared = Regex("""const float RING_OUTER_TAG = ([0-9.]+);""").find(source)
        assertNotNull("outline.mat no longer declares RING_OUTER_TAG", declared)
        assertEquals(MarkerMeshes.RING_OUTER_TAG, declared!!.groupValues[1].toFloat())
    }
}
