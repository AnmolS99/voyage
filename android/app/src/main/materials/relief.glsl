// The relief blend, shared by the fills and the ocean.
//
// Hard light, per channel, exactly as Skia composites BlendMode.Hardlight for
// opaque layers — which is how the flat map draws the same image — so the
// globe and the map shade alike. 0.5 is neutral: the ocean and flat land keep
// their palette colors, shadowed slopes darken proportionally, lit slopes
// lighten toward white.
//
// reliefAt turns a point on the sphere into the image's equirectangular
// coordinates — the inverse of PolygonTriangulator.latLonToSphere, top-left at
// (180°W, 90°N) like the Earth textures. u jumps from 1 to 0 at the
// antimeridian, which would make the GPU pick the smallest mip level for the
// pixels on that line and draw a seam; measuring the gradient of u shifted
// half a turn as well, and keeping the smaller one, removes it. The maths is
// highp because fragment shaders default to half precision on mobile, whose
// 1/2048 steps near u = 1 are half a texel here.

float reliefAt(highp vec3 position) {
    highp vec3 d = normalize(position);
    highp float lat = asin(clamp(d.y, -1.0, 1.0));
    highp float lon = -atan(d.z, d.x);
    highp vec2 uv = vec2(lon * (0.5 / PI) + 0.5, 0.5 - lat / PI);
    highp vec2 dx = dFdx(uv);
    highp vec2 dy = dFdy(uv);
    highp float dxShifted = dFdx(fract(uv.x + 0.5));
    highp float dyShifted = dFdy(fract(uv.x + 0.5));
    if (abs(dxShifted) + abs(dyShifted) < abs(dx.x) + abs(dy.x)) {
        dx.x = dxShifted;
        dy.x = dyShifted;
    }
    return textureGrad(materialParams_relief, uv, dx, dy).r;
}

vec3 hardLight(vec3 base, float shade) {
    return shade <= 0.5
        ? 2.0 * shade * base
        : 1.0 - 2.0 * (1.0 - shade) * (1.0 - base);
}
