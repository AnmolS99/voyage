# voyage website

The marketing site and privacy policy, served by Cloudflare Workers. It's a static
site built by `build.mjs`, with no framework. Whatever the apps also have comes
straight from the repo and is never copied:

| On the site      | Comes from                                              |
| ---------------- | ------------------------------------------------------- |
| `/privacy`       | `PRIVACY.md` at the repo root (the only copy of the policy) |
| Hero globe       | `shared/data/world.geojson` (fills, borders) and `shared/data/textures/` (`earth_texture.jpg` with `relief.png` hard-lit over it, as in the apps with Terrain on), projected at build time |
| Fonts            | `shared/data/fonts/`                                    |
| Icon             | `ios/voyage/Assets.xcassets/AppIcon.appiconset/`        |
| Colors           | Mirrored by hand from `ios/voyage/ColorPalette.swift` in `src/static/styles.css` |

`src/layout.html` wraps every page; `src/index.html` is the landing page's
content; `src/static/` is copied as is (including Cloudflare's `_headers`).
The store links are constants at the top of `build.mjs`.

## Run it locally

```bash
cd web
npm install
npm run dev            # serves http://localhost:4321 and rebuilds on every save (refresh to see it)
```

The build fails if `PRIVACY.md` is missing. To preview with a different policy file,
set `PRIVACY_MD=/path/to/file.md`.

## Deploy (one-time setup)

The site is a Cloudflare Worker with static assets only: `wrangler.jsonc` serves
`dist/` as built, with `dist/404.html` for unknown paths. Workers Builds deploys
it from Git.

1. Cloudflare dashboard → **Workers & Pages** → **Create application** →
   **Import a repository** → pick `AnmolS99/voyage`.
2. Project name: `voyage-globe`. It must match `name` in `wrangler.jsonc`, and
   it becomes the URL, `https://voyage-globe.<account subdomain>.workers.dev`.
3. Build settings:
   - Build command: `npm run build`
   - Deploy command: `npx wrangler deploy`
   - Non-production branch deploy command: `npx wrangler versions upload`
   - Under **Advanced settings**: root directory `web`, and the build variable
     `NODE_VERSION` = `22`
4. After the first deploy, under **Settings → Build**: set the production branch
   to `master` and, optionally, the build watch paths to `web/*`, `PRIVACY.md`
   and `shared/data/*`, so app-only commits don't redeploy the site. Other
   branches get their own preview URLs.

After that, every push to `master` redeploys the site. That includes edits to
`PRIVACY.md`, so the policy shown in the stores always matches the repo.

## Privacy policy URL for the stores

Use `https://voyage-globe.<account subdomain>.workers.dev/privacy` in both places:

- **App Store Connect** → the app → **App Information** → **Privacy Policy URL**
- **Google Play Console** → the app → **Policy and programs → App content →
  Privacy policy**

Changing the App Store's privacy policy URL may require a new version to be
submitted. Play applies the change right away.

## Custom domain later

The Worker → **Settings → Domains & Routes** → add a custom domain. The
`workers.dev` URL keeps working, but switch both stores to the new
`https://<domain>/privacy` so the URL you give out is the one you own.
