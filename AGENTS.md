# Palmyra

Native Kotlin Android TV launcher. See README.md and docs/development.md.

## Hard rules

- Use an emulator for development. Physical-device changes require an explicit user request.
- Runtime dependency: Kotlin stdlib only. No AndroidX, Compose or coroutines.
- No permissions, services, receivers or hidden background work, except the
  owner-approved MediaAccessService below.
- Write only in this repo or toolchain installs and caches.
- Push only when requested. Conventional commits after scripts/verify passes.
- ASCII only in authored code, docs and commit messages. Never print secrets.

## Commands

- source scripts/env.sh: JDK 17 and Android SDK environment.
- ./gradlew assembleDebug assembleRelease: build APKs.
- ./gradlew spotlessApply: format Kotlin and build scripts.
- ./gradlew spotlessCheck detekt: formatting and static analysis.
- ./gradlew compileDebugKotlin lintRelease: compile and Android Lint.
- ./gradlew testDebugUnitTest: pure logic and rendering tests.
- ./gradlew recordRoborazziDebug: update screenshot goldens after visual review.
- ./gradlew verifyRoborazziDebug: compare screenshots with committed goldens.
- ./gradlew checkRuntimeDependencies: enforce runtime dependency whitelist.
- scripts/apk-size: enforce APK and DEX budgets.
- scripts/verify: complete ordered verification, logs under build/verify.
- scripts/generate-blurs: regenerate exact precomputed blur assets after photo edits.
- scripts/compare-reference: render reference fixture and comparison images.
- scripts/install-hooks: install formatting and detekt pre-commit checks.
- scripts/emulator: provision and start the tested arm64 API 31 TV AVD.
- scripts/project-check: shell/Python syntax, ASCII text and private-file guards.
- scripts/smoke: emulator behavior and performance checks.
- scripts/baseline: current Home performance capture.

Changes must pass scripts/verify. Review screenshot changes and record any
untested physical-device behavior in the change description.

## Deliberate tooling exceptions

The owner approved one notification-listener service for Spotify media access.
MediaAccessService is protected by BIND_NOTIFICATION_LISTENER_SERVICE. It does
not read notifications, perform network requests, poll or start playback itself.
Home observes Spotify MediaSession callbacks only while resumed. Android may
keep the empty listener bound while Home is hidden. No other service, receiver
or uses-permission is allowed. Notification access requires an explicit owner
opt-in in Android settings; development and testing remain emulator-only.

Roborazzi/Robolectric native graphics replaces Paparazzi, whose SDK 36 renderer
failed. Screenshot tests render API 28 at 1920x1080 and 320 dpi. Clock and photo
are fixed in tests. Source code still compiles and targets SDK 36.
The external Kotlin plugin and legacy AGP DSL are enabled for plugin compatibility.
Lint disables only toolchain-version update reminders. Targeted suppressions cover
physical right alignment, a programmatically constructed view, screenshot-only
marks, and native Back callbacks. There is no Lint baseline.
The emulator may disable its stock priority-2 Home app. This fallback must never
run against a physical device. Verify Home selection before changing any installed launcher.

Wallpaper blurs are generated with test-only Android rendering on the host. Run
scripts/generate-blurs after changing photos, then scripts/verify. The blur oracle
lives in test sources. Never restore runtime blur work or progressive default JPEG
encoding without measuring the physical TV. Large bitmaps must be released
when Home stops.
