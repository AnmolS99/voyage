package com.anmol.voyage.ui.globe

import android.view.Surface
import com.anmol.voyage.globe.GlobeCamera
import com.anmol.voyage.globe.NamedCountryMesh
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
import com.google.android.filament.Viewport
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
 * (1.003). Border outlines and the atmosphere shell are Phase 7.5/7.2 and are
 * not built here yet.
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
    }

    /**
     * Replaces the scene's geometry with [ocean] and [countries].
     *
     * Safe to call again; the previous upload's engine resources are destroyed
     * first.
     */
    fun setGeometry(ocean: SphereMesh, countries: List<NamedCountryMesh>) {
        releaseGeometry()

        val oceanInstance = materials.ocean.createInstance()
        materialInstances += oceanInstance
        oceanMaterial = oceanInstance
        addRenderable(
            positions = ocean.positions,
            uvs = ocean.uvs,
            indices = ocean.indices,
            material = oceanInstance,
            boundingBox = Box(0f, 0f, 0f, 1.01f, 1.01f, 1.01f),
        )

        for (country in countries) {
            val instance = materials.country.createInstance()
            materialInstances += instance
            countryMaterials[country.name] = instance
            addRenderable(
                positions = country.mesh.positions,
                uvs = country.mesh.uvs,
                indices = country.mesh.indices,
                material = instance,
                // Countries are small patches on the sphere, but a per-country
                // box buys nothing here: they are all within the globe, which is
                // either fully on screen or being zoomed into.
                boundingBox = Box(0f, 0f, 0f, 1.01f, 1.01f, 1.01f),
            )
        }
    }

    /**
     * Paints one country. [colorB] and [gradient] drive the visited+wishlist
     * diagonal; a flat country passes `gradient = 0`.
     */
    fun setCountryColor(name: String, colorA: FloatArray, colorB: FloatArray, gradient: Boolean) {
        val instance = countryMaterials[name] ?: return
        instance.setParameter("colorA", colorA[0], colorA[1], colorA[2], colorA[3])
        instance.setParameter("colorB", colorB[0], colorB[1], colorB[2], colorB[3])
        instance.setParameter("gradient", if (gradient) 1.0f else 0.0f)
    }

    fun setOceanColor(color: FloatArray) {
        oceanMaterial?.setParameter("baseColor", color[0], color[1], color[2], color[3])
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

    private fun addRenderable(
        positions: FloatArray,
        uvs: FloatArray,
        indices: IntArray,
        material: MaterialInstance,
        boundingBox: Box,
    ) {
        val vertexCount = positions.size / 3

        val vertexBuffer = VertexBuffer.Builder()
            .bufferCount(2)
            .vertexCount(vertexCount)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 0)
            .attribute(VertexBuffer.VertexAttribute.UV0, 1, VertexBuffer.AttributeType.FLOAT2, 0, 0)
            .build(engine)
        vertexBuffer.setBufferAt(engine, 0, positions.toDirectBuffer())
        vertexBuffer.setBufferAt(engine, 1, uvs.toDirectBuffer())
        vertexBuffers += vertexBuffer

        val indexBuffer = IndexBuffer.Builder()
            .indexCount(indices.size)
            .bufferType(IndexBuffer.Builder.IndexType.UINT)
            .build(engine)
        indexBuffer.setBuffer(engine, indices.toDirectBuffer())
        indexBuffers += indexBuffer

        val entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(boundingBox)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer)
            .material(0, material)
            .culling(false)
            .build(engine, entity)

        scene.addEntity(entity)
        entities += entity
    }

    private fun releaseGeometry() {
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
        oceanMaterial = null
    }

    companion object {
        /** Well inside `GlobeCamera.MIN_DISTANCE - 1`, as iOS's zNear is. */
        private const val NEAR_PLANE = 0.01
        private const val FAR_PLANE = 100.0

        init {
            Filament.init()
        }
    }
}


private fun FloatArray.toDirectBuffer(): ByteBuffer =
    ByteBuffer.allocateDirect(size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .apply { asFloatBuffer().put(this@toDirectBuffer) }

private fun IntArray.toDirectBuffer(): ByteBuffer =
    ByteBuffer.allocateDirect(size * Int.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .apply { asIntBuffer().put(this@toDirectBuffer) }
