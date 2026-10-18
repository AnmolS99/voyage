# voyage website

The marketing site and privacy policy, served by Cloudflare Pages. It's a static
site built by `build.mjs`, with no framework. Whatever the apps also have comes
straight from the repo and is never copied:

| On the site      | Comes from                                              |
| ---------------- | ------------------------------------------------------- |
| `/privacy`       | `PRIVACY.md` at the repo root (the only copy of the policy) |
| Hero globe       | `shared/data/world.geojson`, drawn to SVG at build time |
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
npm run preview        # builds into dist/ and serves it on http://localhost:3000
```

The build fails if `PRIVACY.md` is missing. To preview with a different policy file,
set `PRIVACY_MD=/path/to/file.md`.

## Deploy (one-time setup)

1. Cloudflare dashboard → **Workers & Pages** → **Create** → **Pages** →
   **Connect to Git** → pick `AnmolS99/voyage`.
2. Project name: this becomes the URL, `https://<name>.pages.dev`. `voyage` is
   probably taken, so something like `voyage-globe`.
3. Build settings:
   - Production branch: `master`
   - Framework preset: **None**
   - Build command: `npm run build`
   - Build output directory: `dist`
   - Root directory: `web`
   - Environment variable: `NODE_VERSION` = `22`
4. Optional: under **Settings → Builds → Build watch paths**, include `web/*`,
   `PRIVACY.md` and `shared/data/*` so app-only commits don't redeploy the site.
   Pull requests get their own preview URLs automatically.

After that, every push to `master` redeploys the site. That includes edits to
`PRIVACY.md`, so the policy shown in the stores always matches the repo.

## Privacy policy URL for the stores

Use `https://<name>.pages.dev/privacy` in both places:

- **App Store Connect** → the app → **App Information** → **Privacy Policy URL**
- **Google Play Console** → the app → **Policy and programs → App content →
  Privacy policy**

Changing the App Store's privacy policy URL may require a new version to be
submitted. Play applies the change right away.

## Custom domain later

Cloudflare Pages project → **Custom domains** → add the domain. The
`pages.dev` URL keeps working, but switch both stores to the new
`https://<domain>/privacy` so the URL you give out is the one you own.
