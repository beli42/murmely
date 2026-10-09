# Bunny Media Editor
![Bunny project logo](bunny_mascot.png)  
Bunny is a free (as in freedom) Android media editor, letting you edit images and videos on your phone. Licensed under the GNU General Public License version 3 (or any later version)

<a href="https://f-droid.org/en/packages/eu.artectrex.bunny/">
	<img src="https://pixeldroid.org/badge-fdroid.png" alt="Get it on F-Droid" width="206">
</a>

Come talk to us on Matrix, at <a href="https://matrix.to/#/#pixeldroid:gnugen.ch">#pixeldroid:gnugen.ch</a> !

## Project structure

Everything is in this one repository:

- `app/` – the Bunny app (start screen, settings, saving)
- `media-editor/` – the photo, collage and video editor (`common`, `photoEditor`, `videoEditor`),
  originally the PixelDroid media editor, extended for Bunny
- `pixel_common/` – shared code from PixelDroid (themes, about page)

Build: `./gradlew assembleDebug`.

Tests:
- Unit (JVM): `./gradlew :app:testDebugUnitTest :photoEditor:testDebugUnitTest`
- Integration (needs a running emulator or device):
  `./gradlew :app:connectedDebugAndroidTest :photoEditor:connectedDebugAndroidTest`

The app's integration tests run inside the installed app; they save and restore its settings.

## Build setup and maintenance

- **Versions** of all libraries and plugins are in `gradle/libs.versions.toml`; build scripts are Kotlin DSL (`*.gradle.kts`).
- **Lint**: warnings fail the build. Findings that already existed are recorded per module in `lint-baseline.xml`, so only new ones count. After fixing old findings, update the baselines:
  `./gradlew updateLintBaseline`
- **Dependency verification**: `gradle/verification-metadata.xml` holds the SHA-256 checksum of every downloaded dependency; Gradle refuses artifacts that don't match. After changing or updating dependencies (e.g. merging a Dependabot pull request), regenerate it and review the diff:
  `./gradlew --write-verification-metadata sha256 assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug`
- **Gradle wrapper**: the distribution checksum is pinned in `gradle/wrapper/gradle-wrapper.properties`, and the workflows check the wrapper JAR against the official Gradle releases.
- **GitHub Actions** are pinned to commit hashes and run with read-only permissions; **Dependabot** proposes weekly updates for libraries and actions.
- **JitPack** is only used for uCrop (`com.github.yalantis`); no other dependency may come from there.

