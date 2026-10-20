# Development

## Toolchain

JDK 17, Android SDK platform 36, build-tools 36.0.0 and platform-tools are required.
The wrapper pins Gradle 9.8.2; the project pins AGP 9.4.1 and Kotlin 2.4.21.
Minimum Android API is 28; compile and target API are 36.

On Apple Silicon macOS, `scripts/env.sh` defaults to Homebrew's OpenJDK 17 and
`~/Library/Android/sdk`. On other systems, set `JAVA_HOME` and `ANDROID_HOME`
before sourcing it. Android Studio is optional.

```sh
source scripts/env.sh
./gradlew assembleDebug assembleRelease
scripts/verify
scripts/install-hooks
```

Verification checks formatting, detekt, runtime dependencies, compilation, unit
and rendering tests, screenshot comparisons, Android Lint, release assembly,
APK/DEX size, manifest constraints and script syntax. Logs are in `build/verify`.
Screenshots use Roborazzi/Robolectric native graphics at API 28, 1920x1080 and
320 dpi, with fixed clocks and photos. There is no Lint baseline.

```sh
./gradlew spotlessApply
./gradlew recordRoborazziDebug
./gradlew verifyRoborazziDebug
```

Review changed goldens before accepting them. `scripts/compare-reference` creates
side-by-side, overlay and difference images in `build/compare`; it requires
ImageMagick. Regenerate matching wallpaper assets after photo changes:

```sh
scripts/generate-blurs
scripts/verify
```

## Emulator

`scripts/emulator` provisions an arm64 API 31 Android TV AVD on Apple Silicon.
After it boots, run:

```sh
ANDROID_SERIAL=emulator-5554 scripts/smoke --fixtures
ANDROID_SERIAL=emulator-5554 scripts/media-smoke
ANDROID_SERIAL=emulator-5554 scripts/baseline
```

Fixtures are local test apps, with no streaming accounts or credentials. Fixture
scripts refuse physical devices. The emulator's stock Home has a higher intent
priority; smoke may disable it on the emulator only. Restore it with:

```sh
adb -s emulator-5554 shell pm enable com.google.android.tvlauncher
```

No physical-device launcher is disabled by the smoke scripts. Emulator timing
and memory measurements do not predict physical TV performance.

## Signing

Release builds use R8 and resource shrinking. Without private signing files they
use the Android debug key. A debug-signed APK cannot update a release-signed app.

For a private release key, supply ignored `signing/release.jks` and
`signing/keystore.properties` with `storePassword` and `keyPassword`. The key alias must be `palmyra`.
Keep both files private and back them up. Never commit or print their contents.

## Customize

Favorites and package names live in `AppCatalog.kt`. Update manifest queries
when adding packages, and add a vector glyph under `res/drawable` if needed.
Missing apps are hidden; Play Store stays last. All apps includes TV and normal
launcher intents, deduplicates packages and sorts by label.

Wallpapers live in `app/src/main/assets/wallpapers`, sorted by filename. Keep
10 to 15 calm 1920x1080 JPEGs, use sequential encoding, and record source,
author and license in `CREDITS.md`. Mirror credits into `app/src/main/assets/CREDITS.txt`.
The current photo persists; visible Home rotates every 15 minutes.

Blur assets are generated on the host, never at runtime. The glass player crop
matches its fixed layout geometry; regenerate it after photo or geometry edits.
Large bitmaps are released when Home stops. Keep Kotlin stdlib as the only
runtime dependency. No AndroidX, Compose or coroutines.
