# RelayTube update channels

RelayTube updates are separate from SmartTube. The app checks only the
`saappleg/RelayTube` GitHub releases when its package name is
`com.relaytube.stable`.

- **Stable** reads `relaytube-update.json` from the latest non-prerelease GitHub
  release.
- **Beta** reads the same manifest name from the rolling `relaytube-beta`
  prerelease.

Users can choose the channel under **Settings → About → RelayTube update
channel**. Prerelease builds default to Beta and production builds default to
Stable. Switching channels does not change the package or erase app data.

## Publishing

Run the **Publish RelayTube update** GitHub Actions workflow manually and choose
the channel. Supply a version name, a version code greater than every previously
published build, and short release notes. The workflow builds signed APKs,
creates the updater manifest, and publishes all architecture variants.

The repository must contain these Actions secrets:

- `SIGNING_KEY`: Base64-encoded PKCS12/JKS keystore
- `KEY_STORE_PASSWORD`
- `ALIAS`
- `KEY_PASSWORD`

Never change the signing key after publishing the first build. Android will
reject an in-place update signed by a different key.

The workflow validates `relaytube-update.json` before publishing and updates the
rolling `relaytube-beta` release in place, so the Beta feed URL remains stable.
The original `relaytube-v0.1.0-beta.1` release predates this manifest and cannot
serve in-app updates by itself.

## Upstream boundary

The following are RelayTube-only and must be excluded from a SmartTube pull
request:

- `.github/workflows/relaytube-release.yml`
- this document
- `RelayUpdatePreferences`
- Relay update resources and the two small settings/presenter hooks
