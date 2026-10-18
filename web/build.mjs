// Builds the static site into dist/. Everything the site shows that the apps
// also have comes from the repo, never a copy: the fonts from
// shared/data/fonts, the globe from shared/data/world.geojson and
// shared/data/textures, the icon from the iOS asset catalog and /privacy from
// the root PRIVACY.md.
import { cpSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { geoArea, geoOrthographic, geoPath } from "d3-geo";
import { marked } from "marked";
import sharp from "sharp";

const WEB = dirname(fileURLToPath(import.meta.url));
const REPO = join(WEB, "..");
const SRC = join(WEB, "src");
const DIST = join(WEB, "dist");

const APP_STORE_URL = "https://apple.co/4t7gZ4Y";
const PLAY_URL = "https://play.google.com/store/apps/details?id=com.anmol.voyage";
const PRIVACY_MD = process.env.PRIVACY_MD ?? join(REPO, "PRIVACY.md");

// A sample traveller for the hero globe, centred on Europe.
const GLOBE = {
  size: 560,
  rotate: [-18, -28],
  visited: ["NO", "SE", "DK", "GB", "FR", "ES", "IT", "DE", "NL", "GR", "TR", "IN", "AE"],
  wishlist: ["IS", "MA", "EG", "PT", "KE"],
};

const read = (path) => readFileSync(path, "utf8");
const fill = (template, values) =>
  template.replace(/\{\{(\w+)\}\}/g, (_, key) => {
    if (!(key in values)) throw new Error(`No value for {{${key}}}`);
    return values[key];
  });

// GeoJSON winds outer rings counter-clockwise, d3-geo clockwise: a ring d3
// reads as covering more than half the sphere is reversed. Coordinates are
// snapped to a grid finer than a pixel at the globe's size, so the inline SVG
// stays small.
const GRID = 0.3;
function forGlobe(geometry) {
  const snap = (ring) =>
    ring
      .map(([lon, lat]) => [Math.round(lon / GRID) * GRID, Math.round(lat / GRID) * GRID])
      .filter((p, i, all) => i === 0 || p[0] !== all[i - 1][0] || p[1] !== all[i - 1][1]);
  const polygon = (rings) =>
    rings.map(snap).filter((ring) => ring.length >= 4).map((ring) =>
      geoArea({ type: "Polygon", coordinates: [ring] }) > 2 * Math.PI ? ring.reverse() : ring);
  switch (geometry.type) {
    case "Polygon": return { ...geometry, coordinates: polygon(geometry.coordinates) };
    case "MultiPolygon": return { ...geometry, coordinates: geometry.coordinates.map(polygon) };
    default: return geometry;
  }
}

// The apps' realistic globe with Terrain on: the Earth texture with plain land
// left unpainted, status fills over it, the shaded relief hard-lit over both
// (as iOS's TerrainRelief does, on encoded values) and the borders on top. The
// texture and relief are projected here into images at twice the globe's size;
// the browser does the hard-light blend.
async function projectTexture(file, channels, projection, px) {
  const width = 2048, height = 1024; // about one texel per pixel at the globe's centre
  const src = await sharp(join(REPO, "shared/data/textures", file))
    .resize(width, height, { fit: "fill" })
    .toColourspace(channels === 1 ? "b-w" : "srgb")
    .raw()
    .toBuffer();
  const out = Buffer.alloc(px * px * channels, 128);
  for (let y = 0; y < px; y++) {
    for (let x = 0; x < px; x++) {
      const lonLat = projection.invert([x + 0.5, y + 0.5]);
      if (!lonLat || Math.hypot(x + 0.5 - px / 2, y + 0.5 - px / 2) > px / 2) continue;
      const u = ((lonLat[0] + 180) / 360) * width - 0.5;
      const v = Math.min(Math.max(((90 - lonLat[1]) / 180) * height - 0.5, 0), height - 1);
      const x0 = Math.floor(u), y0 = Math.floor(v), fx = u - x0, fy = v - y0;
      const xa = (x0 + width) % width, xb = (x0 + 1 + width) % width, yb = Math.min(y0 + 1, height - 1);
      for (let c = 0; c < channels; c++) {
        const at = (xx, yy) => src[(yy * width + xx) * channels + c];
        const top = at(xa, y0) * (1 - fx) + at(xb, y0) * fx;
        const bottom = at(xa, yb) * (1 - fx) + at(xb, yb) * fx;
        out[(y * px + x) * channels + c] = Math.round(top * (1 - fy) + bottom * fy);
      }
    }
  }
  return sharp(out, { raw: { width: px, height: px, channels } });
}

async function globeSvg() {
  const world = JSON.parse(read(join(REPO, "shared/data/world.geojson")));
  const { size } = GLOBE;
  const orthographic = (s) =>
    geoOrthographic().rotate(GLOBE.rotate).translate([s / 2, s / 2]).scale(s / 2).clipAngle(90);
  const path = geoPath(orthographic(size)).digits(1).pointRadius(2.2);

  const px = size * 2;
  const pixels = orthographic(px);
  await (await projectTexture("earth_texture.jpg", 3, pixels, px)).webp({ quality: 80 }).toFile(join(DIST, "globe-texture.webp"));
  await (await projectTexture("relief.png", 1, pixels, px)).webp({ quality: 80 }).toFile(join(DIST, "globe-relief.webp"));

  const statusOf = (id) =>
    GLOBE.visited.includes(id) ? "visited" : GLOBE.wishlist.includes(id) ? "wishlist" : null;
  const countries = world.features
    .map((f) => ({ status: statusOf(f.id), d: path(forGlobe(f.geometry)) }))
    .filter(({ d }) => d);
  const fills = countries.filter((c) => c.status).map((c) => `<path class="${c.status}" d="${c.d}"/>`).join("");
  const borders = countries.map((c) => c.d).join("");
  const sphere = path({ type: "Sphere" });
  const image = (href, cls = "") => `<image href="${href}" width="${size}" height="${size}"${cls && ` class="${cls}"`}/>`;
  return `<svg class="globe" viewBox="0 0 ${size} ${size}" role="img" aria-label="The voyage globe with visited countries in yellow and wishlist countries in purple">
<defs><clipPath id="sphere"><path d="${sphere}"/></clipPath><radialGradient id="shade" cx="38%" cy="32%" r="75%"><stop offset="0" stop-color="#fff" stop-opacity=".12"/><stop offset=".6" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".3"/></radialGradient></defs>
<g clip-path="url(#sphere)" class="surface">${image("/globe-texture.webp")}${fills}${image("/globe-relief.webp", "relief")}</g>
<path class="borders" d="${borders}"/><path d="${sphere}" fill="url(#shade)"/>
</svg>`;
}

function privacyPage() {
  if (!existsSync(PRIVACY_MD)) {
    throw new Error(`${PRIVACY_MD} not found: /privacy is rendered from the repo's PRIVACY.md`);
  }
  const markdown = read(PRIVACY_MD);
  const title = markdown.match(/^#\s+(.+)$/m)?.[1].trim() ?? "Privacy Policy";
  return { title, main: `<article class="prose">${marked.parse(markdown)}</article>` };
}

const layout = read(join(SRC, "layout.html"));
const common = { appStoreUrl: APP_STORE_URL, playUrl: PLAY_URL, year: String(new Date().getFullYear()) };
const page = (file, { title, description, main }) =>
  writeFileSync(join(DIST, file), fill(layout, { ...common, title, description, main }));

rmSync(DIST, { recursive: true, force: true });
mkdirSync(DIST, { recursive: true });
cpSync(join(SRC, "static"), DIST, { recursive: true });
cpSync(join(REPO, "shared/data/fonts"), join(DIST, "fonts"), { recursive: true });
cpSync(join(REPO, "ios/voyage/Assets.xcassets/AppIcon.appiconset/voyage_icon.png"), join(DIST, "icon.png"));

page("index.html", {
  title: "voyage — track the countries you've visited",
  description: "A free 3D globe for iPhone and Android to track the countries you've visited and the ones you dream of. No ads, no accounts.",
  main: fill(read(join(SRC, "index.html")), { ...common, globe: await globeSvg() }),
});

const privacy = privacyPage();
page("privacy.html", {
  title: `${privacy.title} — voyage`,
  description: "How the voyage app handles your data.",
  main: privacy.main,
});

page("404.html", {
  title: "Not found — voyage",
  description: "This page doesn't exist.",
  main: `<section class="narrow"><h1>Off the map</h1><p>There's no page here. <a href="/">Back to the globe</a>.</p></section>`,
});

console.log(`Built ${DIST}`);
