// Builds the static site into dist/. Everything the site shows that the apps
// also have comes from the repo, never a copy: the fonts from
// shared/data/fonts, the globe from shared/data/world.geojson, the icon from
// the iOS asset catalog and /privacy from the root PRIVACY.md.
import { cpSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { geoArea, geoOrthographic, geoPath } from "d3-geo";
import { marked } from "marked";

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

function globeSvg() {
  const world = JSON.parse(read(join(REPO, "shared/data/world.geojson")));
  const { size } = GLOBE;
  const projection = geoOrthographic()
    .rotate(GLOBE.rotate)
    .translate([size / 2, size / 2])
    .scale(size / 2 - 2)
    .clipAngle(90);
  const path = geoPath(projection).digits(1).pointRadius(2.2);
  const statusOf = (id) =>
    GLOBE.visited.includes(id) ? "visited" : GLOBE.wishlist.includes(id) ? "wishlist" : "land";

  const countries = world.features
    .map((f) => [statusOf(f.id), path(forGlobe(f.geometry))])
    .filter(([, d]) => d)
    .map(([status, d]) => `<path class="${status}" d="${d}"/>`)
    .join("");
  const sphere = path({ type: "Sphere" });
  return `<svg class="globe" viewBox="0 0 ${size} ${size}" role="img" aria-label="The voyage globe with visited countries in yellow and wishlist countries in purple">
<defs><radialGradient id="shade" cx="38%" cy="32%" r="75%"><stop offset="0" stop-color="#fff" stop-opacity=".18"/><stop offset=".6" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".35"/></radialGradient></defs>
<path class="ocean" d="${sphere}"/>${countries}<path d="${sphere}" fill="url(#shade)"/>
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
  main: fill(read(join(SRC, "index.html")), { ...common, globe: globeSvg() }),
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
