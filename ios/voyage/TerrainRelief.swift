import MetalKit
import SceneKit
import UIKit

/// The shaded-relief layer both renderers hard-light over everything they paint
/// but the borders and markers, so terrain slopes shade alike on the globe and
/// the map. Android `GlobeMaterials.HARD_LIGHT` / `WorldMap`'s `BlendMode.Hardlight`.
///
/// `relief.png` (bundled from `shared/data/textures/`, made by
/// `scripts/generate_relief_texture.py`) is neutral — 128 — on flat ground and the
/// ocean, darker on slopes facing away from a northwest light and lighter on lit
/// ones. Hard light leaves a color as it is at 0.5, so flat areas keep their
/// palette colors and only slopes change.
enum TerrainRelief {
    static let imageName = "relief.png"

    /// The relief for the flat map, decoded once.
    static let image: UIImage? = UIImage(named: imageName)

    /// The texture the globe's shader modifier samples, as raw 8-bit values with
    /// mipmaps: loaded through MetalKit rather than handed to SceneKit as an
    /// image so it is never read as sRGB, which would move "neutral" off 0.5.
    static func texture(device: MTLDevice) -> MTLTexture? {
        guard let url = Bundle.main.url(forResource: imageName, withExtension: nil) else { return nil }
        return try? MTKTextureLoader(device: device).newTexture(URL: url, options: [
            .SRGB: false,
            .generateMipmaps: true,
            .allocateMipmaps: true,
        ])
    }

    /// A fragment shader modifier that hard-lights the relief over a fill's or the
    /// ocean's final color, reading `relief` (a texture) and `terrainStrength`
    /// (1 shades, 0 leaves every color exactly as it is).
    ///
    /// The relief is looked up by position — the inverse of
    /// `PolygonTriangulator.latLonToSphere`, top-left at (180°W, 90°N) like the Earth
    /// textures — since a fill's UVs span only its own country. `u` jumps from 1 to
    /// 0 at the antimeridian, which would pick the smallest mip level along that
    /// line and draw a seam; taking the gradient of `u` shifted half a turn as well,
    /// and keeping the smaller one, removes it.
    ///
    /// SceneKit renders in linear space, while the map — Core Graphics — blends
    /// gamma-encoded values; the blend is done on encoded values here too, so the
    /// globe and the map shade alike.
    static let shaderModifier = """
    #pragma arguments
    texture2d<float> relief;
    float terrainStrength;

    #pragma body
    if (terrainStrength > 0.0) {
        float3 p = (scn_node.inverseModelViewTransform * float4(_surface.position, 1.0)).xyz;
        float3 d = normalize(p);
        float lat = asin(clamp(d.y, -1.0, 1.0));
        float lon = -atan2(d.z, d.x);
        float2 uv = float2(lon * (0.5 / M_PI_F) + 0.5, 0.5 - lat / M_PI_F);
        float2 gx = dfdx(uv);
        float2 gy = dfdy(uv);
        float sx = dfdx(fract(uv.x + 0.5));
        float sy = dfdy(fract(uv.x + 0.5));
        if (abs(sx) + abs(sy) < abs(gx.x) + abs(gy.x)) {
            gx.x = sx;
            gy.x = sy;
        }
        constexpr sampler reliefSampler(filter::linear, mip_filter::linear, address::repeat);
        float shade = relief.sample(reliefSampler, uv, gradient2d(gx, gy)).r;

        float3 linear = clamp(_output.color.rgb, 0.0, 1.0);
        float3 base = select(1.055 * pow(linear, float3(1.0 / 2.4)) - 0.055, linear * 12.92, linear <= 0.0031308);
        float3 lit = shade <= 0.5
            ? 2.0 * shade * base
            : 1.0 - 2.0 * (1.0 - shade) * (1.0 - base);
        float3 decoded = select(pow((lit + 0.055) / 1.055, float3(2.4)), lit / 12.92, lit <= 0.04045);
        _output.color.rgb = mix(_output.color.rgb, decoded, terrainStrength);
    }
    """

    /// Gives `material` the relief blend, `enabled` or not — see `shaderModifier`.
    static func apply(to material: SCNMaterial, texture: MTLTexture?, enabled: Bool) {
        if material.shaderModifiers?[.fragment] == nil {
            material.shaderModifiers = (material.shaderModifiers ?? [:]).merging([.fragment: shaderModifier]) { $1 }
        }
        if let texture {
            material.setValue(SCNMaterialProperty(contents: texture), forKey: "relief")
        }
        material.setValue(Float(enabled && texture != nil ? 1 : 0), forKey: "terrainStrength")
    }
}
