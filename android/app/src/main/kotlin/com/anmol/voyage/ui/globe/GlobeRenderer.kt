package com.anmol.voyage.ui.globe

import android.view.Surface
import androidx.compose.ui.graphics.Color
import com.anmol.voyage.globe.GlobeCamera
import com.anmol.voyage.globe.MarkerMesh
import com.anmol.voyage.globe.MicrostateDot
import com.anmol.voyage.globe.NamedCountryMesh
import com.anmol.voyage.globe.OutlineMesh
import com.anmol.voyage.globe.SphereMesh
import com.google.android.filament.Box
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndexBuffer
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.VertexBuffer
import com.google.android.filament.View
import com.anmol.voyage.ui.theme.VoyagePalette
import com.google.android.filament.Viewport
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Owns the Filament engine and everything in the globe's scene.
 *
 * Every method here must be called from one thread — the main thread, which is
 * where the [GlobeSurface] render loop runs. Filament's `Engine` is not
 * thread-safe, and the expensive part (triangulating 181 countries) happens off
 * this thread anyway: [setGeometry] receives meshes that are already built and
 * only uploads them.
 *
 * Scene layer order follows iOS: ocean sphere (radius 1.0) → country fills
 * (1.003) → border outlines (1.005) → the selected country's overlay outline
 * (1.006) → microstate dots and the capital star (1.0058…1.0066). The atmosphere
 * shell is Phase 7.2 and is not built here yet.
 */
internal class GlobeRenderer(backgroundColor: FloatArray) {

    private val engine: Engine = Engine.create()
    private val renderer: Renderer = engine.createRenderer()
    private val scene: Scene = engine.createScene()
    private val view: View = engine.createView()
    private val cameraEntity: Int = EntityManager.get().create()
    private val camera: Camera = engine.createCamera(cameraEntity)
    private val materials: GlobeMaterials = GlobeMaterials.build(engine)
    private val skybox: Skybox = Skybox.Builder()
        .color(backgroundColor[0], backgroundColor[1], backgroundColor[2], 1.0f)
        .build(engine)

    private var swapChain: SwapChain? = null
    private var viewportWidth = 0
    private var viewportHeight = 0

    /** Everything created per geometry upload, so a rebuild can release it all. */
    private val entities = mutableListOf<Int>()
    private val vertexBuffers = mutableListOf<VertexBuffer>()
    private val indexBuffers = mutableListOf<IndexBuffer>()
    private val materialInstances = mutableListOf<MaterialInstance>()

    /** Country name → its material instance, so recoloring never rebuilds geometry. */
    private val countryMaterials = mutableMapOf<String, MaterialInstance>()
    private var oceanMaterial: MaterialInstance? = null

    /**
     * The shared border material, and the sectors drawn with it.
     *
     * One instance for every sector, as on iOS: zoom then writes a single
     * `thickness` uniform instead of one per sector.
     */
    private var outlineMaterial: MaterialInstance? = null
    private val outlineSectors = mutableListOf<OutlineSector>()

    /** The selected country's overlay outline, rebuilt whenever the selection changes. */
    private var selectedOutline: Renderable? = null
    private var selectedOutlineMaterial: MaterialInstance? = null

    /** Last thickness scale written to the outline materials — throttles uniform writes. */
    private var outlineScale = Float.NaN

    /** World units per screen pixel last written to the markers; NaN forces a rewrite. */
    private var markerScale = Float.NaN

    /** One microstate's two material instances, plus the size they are drawn at. */
    private class Dot(
        val ring: MaterialInstance,
        val fill: MaterialInstance,
    ) {
        /** Outer and inner radii in pixels, from the country's current style. */
        var ringRadiusPx = 0f
        var fillRadiusPx = 0f
    }

    private val dots = mutableMapOf<String, Dot>()

    /** The selected country's capital star: outline behind, fill on top. */
    private var capitalStar: Renderable? = null
    private var capitalStarOutline: Renderable? = null
    private var capitalStarMaterial: MaterialInstance? = null
    private var capitalStarOutlineMaterial: MaterialInstance? = null
    private var capitalStarRadiusPx = 0f
    private var capitalStarOutlinePx = 0f

    /** A sector outline entity plus the bounding sphere the horizon test uses. */
    private class OutlineSector(val entity: Int, val mesh: OutlineMesh) {
        var visible = true
    }

    /** One uploaded mesh's engine resources, so it can be released on its own. */
    private class Renderable(
        val entity: Int,
        val vertexBuffer: VertexBuffer,
        val indexBuffer: IndexBuffer,
    )

    init {
        view.scene = scene
        view.camera = camera
        scene.skybox = skybox
        // The globe is flat-shaded palette colors on a solid background; none of
        // the post-processing chain (bloom, TAA, tone mapping) does anything for
        // that except cost fill rate and shift the colors away from the palette.
        //
        // This also switches off Filament's linear → sRGB encode, which lives in
        // the same pass. `toFilamentColor` depends on that: it passes palette
        // components through unconverted so they reach the screen exactly. Turn
        // post-processing back on and that function has to convert to linear.
        view.isPostProcessingEnabled = false
    }

    fun onNativeWindowChanged(surface: Surface) {
        swapChain?.let { engine.destroySwapChain(it) }
        swapChain = engine.createSwapChain(surface)
    }

    fun onDetachedFromSurface() {
        swapChain?.let {
            engine.destroySwapChain(it)
            engine.flushAndWait()
            swapChain = null
        }
    }

    fun onResized(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        view.viewport = Viewport(0, 0, width, height)
        camera.setProjection(
            GlobeCamera.FOV_DEGREES,
            width.toDouble() / height.toDouble(),
            NEAR_PLANE,
            FAR_PLANE,
            Camera.Fov.VERTICAL,
        )
    }

    /** Points the camera, without touching geometry. Cheap enough to call per frame. */
    fun setCamera(globeCamera: GlobeCamera) {
        val eye = globeCamera.position
        camera.lookAt(eye.x, eye.y, eye.z, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        setOutlineThickness(globeCamera)
        setMarkerSizes(globeCamera)
        cullFarSideOutlineSectors(globeCamera)
    }

    /**
     * Sizes the markers so they cover the same pixels at every zoom, exactly as
     * the flat map's do outside its pan/zoom matrix.
     *
     * The dots and the star are the one thing on the globe measured in screen
     * terms rather than world terms — a microstate has to stay visible and
     * tappable when zoomed out, which is why the map draws them this way and why
     * the globe follows. iOS instead gives its globe markers a fixed world size
     * and compensates the star with `sqrt(zoomScale)`, which is why its globe
     * and map disagree about marker size and Android's do not.
     */
    private fun setMarkerSizes(globeCamera: GlobeCamera) {
        if (viewportHeight == 0) return
        val scale = globeCamera.pixelSizeInWorld(viewportHeight.toFloat())
        if (!markerScale.isNaN() && abs(scale - markerScale) <= scale * 0.005f) return
        markerScale = scale

        for (dot in dots.values) {
            dot.ring.setParameter("thickness", dot.ringRadiusPx * scale)
            dot.fill.setParameter("thickness", dot.fillRadiusPx * scale)
        }
        // Half a stroke out, half a stroke in — what the map's centered `Stroke`
        // on the star's own path covers. (The map's miter joins also spike a
        // little past the five points; that is a stroking artifact, not a
        // decision, and is not modelled here.)
        capitalStarOutlineMaterial?.setParameter(
            "thickness",
            (capitalStarRadiusPx + capitalStarOutlinePx / 2f) * scale,
        )
        capitalStarMaterial?.setParameter(
            "thickness",
            (capitalStarRadiusPx - capitalStarOutlinePx / 2f) * scale,
        )
    }

    /**
     * Keeps borders a constant width on screen as the camera moves. Without it,
     * zooming in drowns a small country in black.
     *
     * The scale itself is [GlobeCamera.screenScale]; this only pushes it into
     * the two materials, so a zoom writes one float per material instead of
     * rebuilding ~335k vertices.
     */
    private fun setOutlineThickness(globeCamera: GlobeCamera) {
        val scale = globeCamera.screenScale
        // Sub-half-percent moves are invisible and not worth a uniform write per
        // frame during a drag.
        if (!outlineScale.isNaN() && abs(scale - outlineScale) <= 0.005f) return
        outlineScale = scale

        outlineMaterial?.setParameter("thickness", BASE_OUTLINE_THICKNESS * scale)
        selectedOutlineMaterial?.setParameter(
            "thickness",
            BASE_OUTLINE_THICKNESS * scale * SELECTED_OUTLINE_FACTOR,
        )
    }

    /**
     * Hides the outline sectors that are entirely beyond the globe's horizon.
     *
     * The outline mesh dominates the scene's vertex count, and frustum culling
     * never removes the far side — the whole globe is inside the frustum at
     * every zoom that matters. The ocean sphere hides those borders visually,
     * but only after the GPU has transformed every one of their vertices.
     *
     * The test itself is [GlobeCamera.isBeyondHorizon]; this only applies its
     * answer, and only when it changes.
     */
    private fun cullFarSideOutlineSectors(globeCamera: GlobeCamera) {
        if (outlineSectors.isEmpty()) return

        for (sector in outlineSectors) {
            val visible = !globeCamera.isBeyondHorizon(sector.mesh.center, sector.mesh.boundingRadius)
            if (sector.visible == visible) continue
            sector.visible = visible
            if (visible) scene.addEntity(sector.entity) else scene.removeEntity(sector.entity)
        }
    }

    /**
     * Replaces the scene's geometry with [ocean], [countries] and [outlines].
     *
     * Safe to call again; the previous upload's engine resources are destroyed
     * first.
     */
    fun setGeometry(
        ocean: SphereMesh,
        countries: List<NamedCountryMesh>,
        outlines: List<OutlineMesh>,
        microstateDots: List<MicrostateDot>,
    ) {
        releaseGeometry()

        val oceanInstance = materials.ocean.createInstance()
        materialInstances += oceanInstance
        oceanMaterial = oceanInstance
        track(
            addRenderable(
                positions = ocean.positions,
                secondary = Secondary.uvs(ocean.uvs),
                indices = ocean.indices,
                material = oceanInstance,
                boundingBox = GLOBE_BOX,
            ),
        )

        for (country in countries) {
            val instance = materials.country.createInstance()
            materialInstances += instance
            countryMaterials[country.name] = instance
            track(
                addRenderable(
                    positions = country.mesh.positions,
                    secondary = Secondary.uvs(country.mesh.uvs),
                    indices = country.mesh.indices,
                    material = instance,
                    // Countries are small patches on the sphere, but a per-country
                    // box buys nothing here: they are all within the globe, which is
                    // either fully on screen or being zoomed into.
                    boundingBox = GLOBE_BOX,
                ),
            )
        }

        // One material instance shared by every sector — see `outlineMaterial`.
        val outlineInstance = materials.outline.createInstance()
        materialInstances += outlineInstance
        outlineInstance.setFill(GlobeFill(Color.Black, Color.Black, gradient = false))
        outlineInstance.setParameter("thickness", BASE_OUTLINE_THICKNESS)
        outlineMaterial = outlineInstance
        // A written scale is stale the moment the materials are new.
        outlineScale = Float.NaN

        for (outline in outlines) {
            val renderable = addRenderable(
                positions = outline.positions,
                secondary = Secondary.offsets(outline.miters),
                indices = outline.indices,
                material = outlineInstance,
                // A real box per sector, unlike the fills: this is the one place
                // a tight bounding volume pays, because it is what lets Filament
                // skip a sector when zoomed in past it.
                boundingBox = outline.box(),
            )
            track(renderable)
            outlineSectors += OutlineSector(renderable.entity, outline)
        }

        // Microstates are dots in the scene, not an overlay above it. An overlay
        // draws from its own copy of the camera, so during a drag it lands a
        // frame away from the surface underneath and visibly trails the borders;
        // in the scene the dots move under the same matrix as everything else.
        for (dot in microstateDots) {
            val ring = materials.outline.createInstance()
            val fill = materials.outline.createInstance()
            materialInstances += ring
            materialInstances += fill
            dots[dot.name] = Dot(ring = ring, fill = fill)
            track(addMarkerRenderable(dot.ring, ring))
            track(addMarkerRenderable(dot.fill, fill))
        }
    }

    /**
     * Paints one microstate's dot. [radiusPx] and [borderWidthPx] are the map's
     * own screen measurements: the ring reaches half a border outside them and
     * the fill stops half a border inside, which is what a stroked circle looks
     * like.
     */
    fun setDotAppearance(
        name: String,
        fill: GlobeFill,
        border: GlobeFill,
        radiusPx: Float,
        borderWidthPx: Float,
    ) {
        val dot = dots[name] ?: return
        dot.ring.setFill(border)
        dot.fill.setFill(fill)
        dot.ringRadiusPx = radiusPx + borderWidthPx / 2f
        dot.fillRadiusPx = radiusPx - borderWidthPx / 2f
        // A new size only reaches the shader on the next size pass, which the
        // render loop runs every frame; nothing here needs the camera.
        markerScale = Float.NaN
    }

    /**
     * Marks [capital] with a star, or clears it when null.
     *
     * Rebuilt on selection rather than kept per country: unlike the dots, only
     * one star is ever on screen.
     */
    fun setCapitalStar(outline: MarkerMesh?, fill: MarkerMesh?, radiusPx: Float, outlinePx: Float) {
        releaseCapitalStar()
        if (outline == null || fill == null) return

        capitalStarRadiusPx = radiusPx
        capitalStarOutlinePx = outlinePx

        val outlineInstance = materials.outline.createInstance()
        outlineInstance.setFill(GlobeFill(VoyagePalette.capitalMarkerOutline, VoyagePalette.capitalMarkerOutline, false))
        capitalStarOutlineMaterial = outlineInstance
        capitalStarOutline = addMarkerRenderable(outline, outlineInstance)

        val fillInstance = materials.outline.createInstance()
        fillInstance.setFill(GlobeFill(VoyagePalette.capitalMarker, VoyagePalette.capitalMarker, false))
        capitalStarMaterial = fillInstance
        capitalStar = addMarkerRenderable(fill, fillInstance)

        markerScale = Float.NaN
    }

    /**
     * Draws [outline] as the selected country's overlay border — thicker than
     * its neighbours, painted in the country's status color, and built at a
     * larger radius so it sits above them.
     *
     * Passing null clears it. The mesh is uploaded fresh each time the selection
     * changes rather than kept per country: one country's border is a small
     * upload, and keeping 206 of them resident on the GPU is not.
     */
    fun setSelectedOutline(outline: OutlineMesh?, color: GlobeFill?) {
        releaseSelectedOutline()
        if (outline == null || color == null) return

        val instance = materials.outline.createInstance()
        instance.setFill(color)
        instance.setParameter(
            "thickness",
            BASE_OUTLINE_THICKNESS *
                (if (outlineScale.isNaN()) 1.0f else outlineScale) *
                SELECTED_OUTLINE_FACTOR,
        )
        selectedOutlineMaterial = instance

        selectedOutline = addRenderable(
            positions = outline.positions,
            secondary = Secondary.offsets(outline.miters),
            indices = outline.indices,
            material = instance,
            boundingBox = outline.box(),
        )
    }

    /** Paints one country. */
    fun setCountryColor(name: String, fill: GlobeFill) {
        countryMaterials[name]?.setFill(fill)
    }

    /**
     * Writes a [GlobeFill] into a material. The country and outline shaders take
     * the same trio — flat [GlobeFill.colorA], or the visited+wishlist diagonal
     * between both colors when `gradient` is set — so one writer covers fills,
     * borders and markers alike.
     */
    private fun MaterialInstance.setFill(fill: GlobeFill) {
        val a = fill.colorA.toFilamentColor()
        val b = fill.colorB.toFilamentColor()
        setParameter("colorA", a[0], a[1], a[2], a[3])
        setParameter("colorB", b[0], b[1], b[2], b[3])
        setParameter("gradient", if (fill.gradient) 1.0f else 0.0f)
    }

    fun setOceanColor(color: Color) {
        val components = color.toFilamentColor()
        oceanMaterial?.setParameter("baseColor", components[0], components[1], components[2], components[3])
    }

    /** Renders one frame. Returns false when the surface is not ready. */
    fun render(frameTimeNanos: Long): Boolean {
        val chain = swapChain ?: return false
        if (viewportWidth == 0 || viewportHeight == 0) return false
        if (renderer.beginFrame(chain, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
        }
        return true
    }

    fun destroy() {
        // Filament destroys in reverse creation order, and the engine last.
        engine.flushAndWait()
        releaseGeometry()
        swapChain?.let { engine.destroySwapChain(it) }
        scene.skybox = null
        engine.destroySkybox(skybox)
        materials.destroy(engine)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
        engine.destroyRenderer(renderer)
        engine.destroy()
    }

    /**
     * The per-vertex attribute that rides alongside positions.
     *
     * Fills carry UVs for the visited+wishlist diagonal; outlines carry the
     * miter direction the outline material widens them along, with that same
     * gradient parameter in its fourth component.
     */
    private class Secondary(
        val data: FloatArray,
        val attribute: VertexBuffer.VertexAttribute,
        val type: VertexBuffer.AttributeType,
    ) {
        companion object {
            fun uvs(data: FloatArray) = Secondary(
                data,
                VertexBuffer.VertexAttribute.UV0,
                VertexBuffer.AttributeType.FLOAT2,
            )

            fun offsets(data: FloatArray) = Secondary(
                data,
                VertexBuffer.VertexAttribute.CUSTOM0,
                VertexBuffer.AttributeType.FLOAT4,
            )
        }
    }

    private fun addRenderable(
        positions: FloatArray,
        secondary: Secondary,
        indices: IntArray,
        material: MaterialInstance,
        boundingBox: Box,
    ): Renderable {
        val vertexCount = positions.size / 3

        val vertexBuffer = VertexBuffer.Builder()
            .bufferCount(2)
            .vertexCount(vertexCount)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 0)
            .attribute(secondary.attribute, 1, secondary.type, 0, 0)
            .build(engine)
        vertexBuffer.setBufferAt(engine, 0, positions.toDirectBuffer())
        vertexBuffer.setBufferAt(engine, 1, secondary.data.toDirectBuffer())

        val indexBuffer = IndexBuffer.Builder()
            .indexCount(indices.size)
            .bufferType(IndexBuffer.Builder.IndexType.UINT)
            .build(engine)
        indexBuffer.setBuffer(engine, indices.toDirectBuffer())

        val entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(boundingBox)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer)
            .material(0, material)
            // Frustum culling, on because [boundingBox] is honest for every
            // caller. It does nothing for the ocean and the fills, which are
            // given the whole globe's box, and everything for the outline
            // sectors, which are given their own.
            .culling(true)
            .build(engine, entity)

        scene.addEntity(entity)
        return Renderable(entity, vertexBuffer, indexBuffer)
    }

    /**
     * Uploads one marker. Every vertex sits at the marker's center until the
     * material pushes it out, so the bounding box has to cover where the marker
     * will end up rather than where its vertices are — [MARKER_BOX_HALF_EXTENT]
     * is comfortably wider than any marker at any zoom.
     */
    private fun addMarkerRenderable(mesh: MarkerMesh, material: MaterialInstance): Renderable =
        addRenderable(
            positions = mesh.positions,
            secondary = Secondary.offsets(mesh.offsets),
            indices = mesh.indices,
            material = material,
            boundingBox = Box(
                mesh.center.x, mesh.center.y, mesh.center.z,
                MARKER_BOX_HALF_EXTENT, MARKER_BOX_HALF_EXTENT, MARKER_BOX_HALF_EXTENT,
            ),
        )

    private fun releaseCapitalStar() {
        capitalStar?.let { destroy(it) }
        capitalStar = null
        capitalStarOutline?.let { destroy(it) }
        capitalStarOutline = null
        capitalStarMaterial?.let { engine.destroyMaterialInstance(it) }
        capitalStarMaterial = null
        capitalStarOutlineMaterial?.let { engine.destroyMaterialInstance(it) }
        capitalStarOutlineMaterial = null
    }

    /** Adds a renderable to the bulk geometry, which [releaseGeometry] frees together. */
    private fun track(renderable: Renderable) {
        entities += renderable.entity
        vertexBuffers += renderable.vertexBuffer
        indexBuffers += renderable.indexBuffer
    }

    private fun destroy(renderable: Renderable) {
        scene.removeEntity(renderable.entity)
        engine.destroyEntity(renderable.entity)
        EntityManager.get().destroy(renderable.entity)
        engine.destroyVertexBuffer(renderable.vertexBuffer)
        engine.destroyIndexBuffer(renderable.indexBuffer)
    }

    private fun releaseSelectedOutline() {
        selectedOutline?.let { destroy(it) }
        selectedOutline = null
        selectedOutlineMaterial?.let { engine.destroyMaterialInstance(it) }
        selectedOutlineMaterial = null
    }

    private fun releaseGeometry() {
        releaseSelectedOutline()
        releaseCapitalStar()
        for (entity in entities) {
            scene.removeEntity(entity)
            engine.destroyEntity(entity)
            EntityManager.get().destroy(entity)
        }
        entities.clear()
        vertexBuffers.forEach { engine.destroyVertexBuffer(it) }
        vertexBuffers.clear()
        indexBuffers.forEach { engine.destroyIndexBuffer(it) }
        indexBuffers.clear()
        materialInstances.forEach { engine.destroyMaterialInstance(it) }
        materialInstances.clear()
        countryMaterials.clear()
        outlineSectors.clear()
        dots.clear()
        oceanMaterial = null
        outlineMaterial = null
        markerScale = Float.NaN
    }

    companion object {
        /** Well inside `GlobeCamera.MIN_DISTANCE - 1`, as iOS's zNear is. */
        private const val NEAR_PLANE = 0.01
        private const val FAR_PLANE = 100.0

        /**
         * Border thickness in world units at the default camera distance —
         * iOS `Coordinator.baseOutlineThickness`, and the selected overlay's
         * multiple of it.
         */
        private const val BASE_OUTLINE_THICKNESS = 0.0015f
        private const val SELECTED_OUTLINE_FACTOR = 5.0f / 3.0f

        /** Encloses the whole globe, outlines and all. */
        private val GLOBE_BOX = Box(0f, 0f, 0f, 1.01f, 1.01f, 1.01f)

        /**
         * Half-width of a marker's bounding box. Markers are a point of geometry
         * expanded by the material, so this has to bound the *drawn* size: at the
         * furthest zoom a 6dp star is well under 0.05 world units.
         */
        private const val MARKER_BOX_HALF_EXTENT = 0.08f

        init {
            Filament.init()
        }
    }
}


/**
 * The mesh's bounding sphere as the axis-aligned box Filament culls against,
 * grown by the widest the outline material can push a vertex out.
 */
private fun OutlineMesh.box(): Box {
    val half = boundingRadius + 0.01f
    return Box(center.x, center.y, center.z, half, half, half)
}

private fun FloatArray.toDirectBuffer(): ByteBuffer =
    ByteBuffer.allocateDirect(size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .apply { asFloatBuffer().put(this@toDirectBuffer) }

private fun IntArray.toDirectBuffer(): ByteBuffer =
    ByteBuffer.allocateDirect(size * Int.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .apply { asIntBuffer().put(this@toDirectBuffer) }
