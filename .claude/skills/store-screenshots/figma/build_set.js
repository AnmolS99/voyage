// Builds one store-screenshot set in Figma: a Section holding one frame per
// scene, each with its eyebrow, headline, subline and an empty "screen" slot
// that upload_assets fills with the captured app screen afterwards.
//
// Run through use_figma with CONFIG prepended (see SKILL.md, step 4):
//   const CONFIG = { version, set, scenes, shotAspect };
// It is self-contained Plugin API code: top-level await, returns node IDs.

// Voyage design-system tokens (https://claude.ai/artifact/3jrQLK8zhLqB26NEFQUFYm,
// project/tokens.json). Restated here only because Figma can't read the system;
// keep in step with it.
const T = {
  light: { bgTop: '#faf5ed', bgBottom: '#f2e8de', text: '#33261a', text2: '#66594c' },
  dark: { bgTop: '#262633', bgBottom: '#1a1a1f', text: '#ffffff', text2: '#b2b2bf' },
  ocean: '#2f86a6', accent: '#d98c59', accentLabel: '#33261a',
  track: { light: '#e6e0d9', dark: '#40404c' },
};
// The iPhone bezel the old set used (an image already in this file), and where
// it sits relative to the screen it frames.
const IPHONE_BEZEL = { hash: '85a8d4cb5dd8334894f69f8c4156fea900e1fc9a', x: -0.0597, y: -0.02632, w: 1.1194, h: 1.05264, radius: 0.0656 };

const rgb = (hex) => ({ r: parseInt(hex.slice(1, 3), 16) / 255, g: parseInt(hex.slice(3, 5), 16) / 255, b: parseInt(hex.slice(5, 7), 16) / 255 });
const solid = (hex, opacity = 1) => ({ type: 'SOLID', color: rgb(hex), opacity });
const vGradient = (top, bottom) => ({
  type: 'GRADIENT_LINEAR', gradientTransform: [[0, 1, 0], [-1, 0, 1]],
  gradientStops: [{ position: 0, color: { ...rgb(top), a: 1 } }, { position: 1, color: { ...rgb(bottom), a: 1 } }],
});

const FONTS = {
  display: { family: 'Newsreader', style: 'Medium' },
  text: { family: 'Schibsted Grotesk', style: 'Regular' },
  label: { family: 'Schibsted Grotesk', style: 'SemiBold' },
};
await Promise.all(Object.values(FONTS).map((f) => figma.loadFontAsync(f)));

const { version, set, scenes, shotAspect } = CONFIG;
const W = set.width, H = set.height;
const isTablet = set.chrome !== 'iphone' && W / H > 0.6;
const GAP = Math.round(W * 0.08);

// Each version gets its own page, titled with the version number; sets stack
// down it in the order they are built.
let page = figma.root.children.find((p) => p.name === version);
if (!page) { page = figma.createPage(); page.name = version; }
await figma.setCurrentPageAsync(page);
const kids = page.children;
const originX = kids.length ? Math.min(...kids.map((n) => n.x)) : 0;
const originY = kids.length ? Math.max(...kids.map((n) => n.y + n.height)) + 400 : 0;

const section = figma.createSection();
section.name = set.label;
page.appendChild(section);

function text(chars, font, size, color, { lineHeight, tracking = 0, width }) {
  const t = figma.createText();
  t.fontName = font;
  t.characters = chars;
  t.fontSize = size;
  t.fills = [solid(color)];
  t.textAlignHorizontal = 'CENTER';
  if (lineHeight) t.lineHeight = { unit: 'PERCENT', value: lineHeight * 100 };
  t.letterSpacing = { unit: 'PERCENT', value: tracking };
  if (width) { t.resize(width, t.height); t.textAutoResize = 'HEIGHT'; }
  return t;
}

// Deterministic star field for dark frames: the app's night-travel backdrop.
function stars(frame, seed) {
  let s = seed * 9301 + 49297;
  const rand = () => ((s = (s * 9301 + 49297) % 233280) / 233280);
  for (let i = 0; i < 70; i++) {
    const d = W * (0.002 + rand() * 0.004);
    const e = figma.createEllipse();
    e.resize(d, d);
    e.x = rand() * W; e.y = rand() * H * 0.75;
    e.fills = [solid('#ffffff', 0.25 + rand() * 0.55)];
    frame.appendChild(e);
  }
}

const frames = [], screens = [];
scenes.forEach((scene, i) => {
  const theme = T[scene.frame];
  const f = figma.createFrame();
  f.name = `${set.id}/${String(i + 1).padStart(2, '0')}-${scene.id}`;
  f.resize(W, H);
  f.clipsContent = true;
  f.fills = [vGradient(theme.bgTop, theme.bgBottom)];
  section.appendChild(f);
  f.x = i * (W + GAP); f.y = 0;
  if (scene.frame === 'dark') stars(f, i + 1);

  // A globe rising from the bottom edge: ocean is Voyage's identity colour #1.
  const horizon = figma.createEllipse();
  horizon.name = 'horizon';
  const D = Math.max(W, H) * 1.5;
  horizon.resize(D, D);
  horizon.x = (W - D) / 2; horizon.y = H * 0.70;
  horizon.fills = [solid(T.ocean, scene.frame === 'dark' ? 0.9 : 1)];
  f.appendChild(horizon);

  // Copy block: accent pill eyebrow, Newsreader headline, Grotesk subline.
  const copy = figma.createAutoLayout('VERTICAL', { name: 'copy', itemSpacing: Math.round(W * 0.028), counterAxisAlignItems: 'CENTER' });
  copy.fills = [];
  f.appendChild(copy);
  const textW = Math.round(W * (isTablet ? 0.72 : 0.86));

  const pill = figma.createAutoLayout('HORIZONTAL', { name: 'eyebrow', cornerRadius: 9999 });
  pill.paddingLeft = pill.paddingRight = Math.round(W * 0.03);
  pill.paddingTop = pill.paddingBottom = Math.round(W * 0.012);
  pill.fills = [solid(T.accent)];
  pill.appendChild(text(scene.eyebrow, FONTS.label, Math.round(W * 0.034), T.accentLabel, { tracking: 2 }));
  copy.appendChild(pill);
  copy.appendChild(text(scene.headline, FONTS.display, Math.round(W * (isTablet ? 0.066 : 0.084)), theme.text, { lineHeight: 1.08, tracking: -1.5, width: textW }));
  copy.appendChild(text(scene.subline, FONTS.text, Math.round(W * (isTablet ? 0.03 : 0.038)), theme.text2, { lineHeight: 1.35, width: Math.round(textW * 0.92) }));
  copy.x = (W - copy.width) / 2;
  copy.y = Math.round(W * (isTablet ? 0.06 : 0.09));
  frames.push(f);
});

// Device: as wide as fits, running a little off the bottom edge, and at the
// same height in every frame — below the tallest copy block — so a one-line
// headline doesn't lift its device above its neighbours'.
const top = Math.max(...frames.map((f) => { const c = f.findOne((n) => n.name === 'copy'); return c.y + c.height; })) + Math.round(W * 0.06);
const bleed = 0.08;
const outer = set.chrome === 'iphone' ? { w: IPHONE_BEZEL.w, h: IPHONE_BEZEL.h } : { w: 1, h: 1 };
const sw = Math.min(W * (isTablet ? 0.78 : 0.76) / outer.w, (H - top) / (shotAspect * outer.h * (1 - bleed)));
const sh = sw * shotAspect;
const sx = (W - sw) / 2, sy = top + (set.chrome === 'iphone' ? -IPHONE_BEZEL.y * sh : 0);

frames.forEach((f, i) => {
  const scene = scenes[i];

  if (set.chrome === 'ipad') {
    const b = Math.round(sw * 0.03);
    const body = figma.createRectangle();
    body.name = 'bezel';
    body.resize(sw + 2 * b, sh + 2 * b);
    body.x = sx - b; body.y = sy - b;
    body.cornerRadius = sw * 0.045 + b;
    body.fills = [solid('#1c1c1e')];
    body.strokes = [solid('#3a3a3c')]; body.strokeWeight = Math.max(2, b * 0.12);
    body.effects = [{ type: 'DROP_SHADOW', color: { r: 0, g: 0, b: 0, a: 0.3 }, offset: { x: 0, y: W * 0.02 }, radius: W * 0.05, spread: 0, visible: true, blendMode: 'NORMAL' }];
    f.appendChild(body);
  }

  const screen = figma.createRectangle();
  screen.name = `screen:${scene.id}`;
  screen.resize(sw, sh);
  screen.x = sx; screen.y = sy;
  screen.cornerRadius = sw * (set.chrome === 'iphone' ? IPHONE_BEZEL.radius : set.chrome === 'ipad' ? 0.045 : 0.06);
  screen.fills = [solid(T.track[scene.frame])];
  if (set.chrome === 'android') {
    // Play discourages device frames: a rounded screen with the overlay shadow.
    screen.effects = [{ type: 'DROP_SHADOW', color: { r: 0, g: 0, b: 0, a: 0.3 }, offset: { x: 0, y: W * 0.02 }, radius: W * 0.05, spread: 0, visible: true, blendMode: 'NORMAL' }];
  }
  f.appendChild(screen);

  if (set.chrome === 'iphone') {
    const bezel = figma.createRectangle();
    bezel.name = 'bezel';
    bezel.resize(sw * IPHONE_BEZEL.w, sh * IPHONE_BEZEL.h);
    bezel.x = sx + IPHONE_BEZEL.x * sw; bezel.y = sy + IPHONE_BEZEL.y * sh;
    bezel.fills = [{ type: 'IMAGE', imageHash: IPHONE_BEZEL.hash, scaleMode: 'FILL' }];
    f.appendChild(bezel);
  }
  screens.push(screen.id);
});

section.resizeWithoutConstraints(scenes.length * (W + GAP) - GAP, H);
section.x = originX; section.y = originY;
return { pageId: page.id, sectionId: section.id, frameIds: frames.map((f) => f.id), screenIds: screens, sceneIds: scenes.map((s) => s.id) };
