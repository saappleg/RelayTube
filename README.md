# RelayTube

RelayTube is an open-source media client designed for Android TV, Google TV, and
Android TV boxes. It provides a remote-friendly, 10-foot interface for browsing,
searching, and playing YouTube content.

![RelayTube home screen](./images/browse_home.png)

> RelayTube is currently distributed through GitHub release channels. It is not
> an official Google or YouTube application.

[Releases](https://github.com/saappleg/RelayTube/releases) ·
[Issues](https://github.com/saappleg/RelayTube/issues) ·
[License](./LICENSE)

## Features

- Android TV/Leanback navigation and a TV-optimized browsing experience
- Search, subscriptions, history, playlists, and channel browsing
- Video playback controls for quality, speed, audio/video tracks, and codecs
- HDR and high-resolution playback when supported by the source and device
- Optional SponsorBlock and DeArrow integrations
- Live chat and other playback-related controls
- Casting, remote-control integration, and voice-search bridge support where the
  device supports them
- Background playback and picture-in-picture options on compatible devices
- Customizable playback and interface settings

Feature availability depends on the device, account, network, and changes to
the services RelayTube connects to.

## Device support

RelayTube is an Android TV application. The manifest requires the Android
Leanback feature, so it is intended for devices with a TV interface and a
remote or D-pad.

| Target | Support in this repository |
| --- | --- |
| Android TV and Google TV | Supported target platforms |
| Android TV boxes and TV sticks | Supported when the device provides Android TV/Leanback compatibility |
| Android-based Fire TV devices | Compatibility is device- and OS-dependent; verify before deployment |
| Phones and tablets | Not an officially supported layout or input target |
| Samsung Tizen, LG webOS, Roku, Apple TV, and other non-Android platforms | Not supported |

The alpha, beta, and stable application variants declare `minSdkVersion 17`
(Android 4.2/API 17). The F-Droid variant raises its minimum to API 21.
Playback features such as 4K, HDR, high frame rates, AV1, and VP9 depend on
the hardware decoder, Android firmware, network, and video source.

## Release channels

RelayTube uses separate release channels so users can choose how much change
they want to accept:

| Channel | Intended use | Application variant | Updates |
| --- | --- | --- | --- |
| Alpha | Early previews and UI or feature testing | `stalpha`, package `com.relaytube.beta` | Built-in updater is configured for the alpha manifest |
| Beta | Public testing and faster fixes | `stbeta`, package `com.relaytube.beta` | Built-in updater is configured for the beta manifest |
| Stable | General use after testing | `ststable`, package `com.relaytube.stable` | Built-in updater is configured for the stable manifest |
| F-Droid | Builds intended for F-Droid distribution | `stfdroid`, package `com.relaytube.fdroid` | No GitHub updater manifest is defined for this flavor |

Alpha, beta, and stable APKs are not interchangeable merely because their
filenames look similar. Android can only perform an in-place update when the
package identity and signing key match. Back up app data before switching to a
different package or signing source.

## Installation

Download APKs only from the official
[RelayTube GitHub releases page](https://github.com/saappleg/RelayTube/releases).
Select the desired channel and an ABI that matches the device:

- `arm64-v8a`: most modern 64-bit ARM Android TV devices
- `armeabi-v7a`: older or 32-bit ARM devices
- `x86`: x86 Android devices or development environments that require it
- `universal`: the largest package; contains the supported ABIs and is the
  simplest choice when the device ABI is unknown

Sideloading requires permission to install apps from the file manager or
Downloader application you use. You can also install an APK with ADB:

```sh
adb connect <device-ip-address>
adb install -r RelayTube_<channel>_<version>_<abi>.apk
```

If Android reports that the APK is incompatible, check the Android API level,
ABI, available storage, package identity, and signing key. Removing an existing
installation can resolve a package/signature conflict, but it can also remove
local app data.

## Updates from GitHub

Alpha, beta, and stable builds include the app's update checker. From the app, open
the About settings and select **Check for updates**. Automatic checks can also
notify about an available update. The app downloads an ABI-appropriate APK,
shows the changelog, and then hands installation to Android. A normal
sideloaded application cannot silently install an update, so an Android
installation confirmation is expected.

The current source contains these channel-specific manifest URLs:

```text
Alpha:  https://github.com/saappleg/RelayTube/releases/download/relaytube-alpha/relaytube-update.json
Beta:   https://github.com/saappleg/RelayTube/releases/download/relaytube-beta/relaytube-update.json
Stable: https://github.com/saappleg/RelayTube/releases/download/relaytube-stable/relaytube-update.json
```

Each channel tag is a rolling GitHub release updated by the release workflow.
Each JSON manifest lists release metadata, a numeric `versionCode`, a display
`versionName`, changelog entries, and one or more APK download URLs. The
updater chooses the highest version code and prefers the ABI-specific download
URL when one is available.

The F-Droid flavor has no GitHub update manifest and should be updated through
its distribution channel when published. If a channel manifest has not yet
been published, use the manual installation instructions above.

## Building from source

### Requirements

- JDK 17 (the known working setup for this checkout)
- Android SDK with the platform and build tools required by the project
- Git with submodule support

The project uses the Gradle wrapper, Gradle 7.5, Android Gradle Plugin 7.4.2,
Kotlin 1.8.10, and compile SDK 34. The repository's shared modules are included
as Git submodules.

Clone and initialize the project:

```sh
git clone https://github.com/saappleg/RelayTube.git
cd RelayTube
git submodule update --init --recursive
chmod +x gradlew
```

Build a release APK for a channel:

```sh
./gradlew :smarttubetv:assembleStbetaRelease
./gradlew :smarttubetv:assembleStalphaRelease
./gradlew :smarttubetv:assembleStstableRelease
./gradlew :smarttubetv:assembleStfdroidRelease
```

For local development, use the corresponding debug task, for example:

```sh
./gradlew :smarttubetv:assembleStbetaDebug
```

APK output is written below:

```text
smarttubetv/build/outputs/apk/<variant>/release/
```

The build produces ABI-specific APKs for `arm64-v8a`, `armeabi-v7a`, and
`x86`, plus a universal APK. Release signing is optional for local builds. To
sign release artifacts with a project keystore, create a local, uncommitted
`keystore.properties` file with the values expected by the build:

```properties
storeFile=/absolute/path/to/key.jks
storePassword=<store-password>
keyAlias=<key-alias>
keyPassword=<key-password>
```

Never commit keystores, passwords, or signing properties.

## GitHub Actions and releases

The CI workflow runs on pushes to `master` and through manual workflow
dispatch. It initializes submodules, runs beta lint, builds the beta release,
optionally submits APKs to VirusTotal when the repository secret is configured,
and uploads the four beta APKs as workflow artifacts.

The `Publish RelayTube update` workflow is manually dispatched with an `alpha`,
`beta`, or `stable` channel, version name/code, and release notes. It builds the
matching signed flavor, uploads ABI-specific and universal APKs to the rolling
channel release, and publishes `relaytube-update.json` for the in-app updater.
The workflow requires the repository signing secrets (`SIGNING_KEY`,
`KEY_STORE_PASSWORD`, `ALIAS`, and `KEY_PASSWORD`). Version codes must increase
for every published channel update.

## Known limitations

- RelayTube is designed for Android TV and remote/D-pad input; phones, tablets,
  and touch-first layouts are not supported targets.
- YouTube service, authentication, playback, and search behavior can change
  without notice and may temporarily break an app feature.
- Codec, resolution, HDR, frame-rate, and picture-in-picture support varies by
  device firmware and hardware.
- Casting and voice-search bridge behavior depends on the device and its
  installed system applications.
- Updates require Android's installation confirmation; silent installation is
  not available to a normal sideloaded app.
- In-app alpha/beta/stable updates depend on the externally published GitHub
  JSON manifests described above.

## Contributing and support

Please search existing
[issues](https://github.com/saappleg/RelayTube/issues) before opening a new
report. Include the RelayTube version, release channel, device model, Android
version, reproduction steps, and relevant logs when reporting a problem.

Pull requests should keep channel-specific behavior, package identities, and
release artifacts clearly separated. Do not include private signing material or
downloaded build outputs in commits.

## License

RelayTube is distributed under the [MIT License](./LICENSE).
