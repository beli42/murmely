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
  originally the PixelDroid media editor, extended for Bunny. Videos are played and converted
  with Media3 (no FFmpeg).
- `pixel_common/` – shared code from PixelDroid (themes, about page)

Build: `./gradlew assembleDebug`.

Tests:
- Unit (JVM): `./gradlew :app:testDebugUnitTest :photoEditor:testDebugUnitTest :videoEditor:testDebugUnitTest`
- Integration (needs a running emulator or device):
  `./gradlew :app:connectedDebugAndroidTest :photoEditor:connectedDebugAndroidTest :videoEditor:connectedDebugAndroidTest`

The app's integration tests run inside the installed app; they save and restore its settings.

## Build setup and maintenance

- **Versions** of all libraries and plugins are in `gradle/libs.versions.toml`; build scripts are Kotlin DSL (`*.gradle.kts`).
- **Lint**: warnings fail the build. Findings that already existed are recorded per module in `lint-baseline.xml`, so only new ones count. After fixing old findings, update the baselines:
  `./gradlew updateLintBaseline`
- **Dependency verification**: `gradle/verification-metadata.xml` holds the SHA-256 checksum of every downloaded dependency; Gradle refuses artifacts that don't match. After changing or updating dependencies (e.g. merging a Dependabot pull request), regenerate it and review the diff:
  Always in **one single run** on an **empty Gradle home**, with an emulator or device connected (the device tests need their own dependencies):
  `rm gradle/verification-metadata.xml && GRADLE_USER_HOME=/tmp/gradle-fresh ./gradlew --write-verification-metadata sha256 --continue --no-daemon clean assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug connectedDebugAndroidTest && rm -rf /tmp/gradle-fresh`
  Why: with an already filled Gradle home, Gradle doesn't read parent POMs, BOMs or the POM files of the licence list (AboutLibraries) again, so their checksums are missing and builds on a fresh machine (GitHub Actions) fail. A second run on the same home can drop entries the first one wrote, hence one run for everything. It downloads all dependencies once and takes a while.
- **Licence list** (About → Licences): generated at build time by AboutLibraries for all libraries of the app. The bundled stickers and fonts are not Gradle dependencies; their entries and full licence texts are in `config/aboutlibraries/`. Tap an entry to read its full licence.
  Gradle only adds entries and never removes the ones of versions no longer used. After larger updates, delete the file first and generate it from scratch.
- **Lint baselines** recognise known findings by their message. If a message changes (e.g. a translation was added, so a language disappears from "not translated in ..."), the finding counts as new; run `./gradlew updateLintBaseline`.
- **Gradle wrapper**: the distribution checksum is pinned in `gradle/wrapper/gradle-wrapper.properties`, and the workflows check the wrapper JAR against the official Gradle releases.
- **GitHub Actions** are pinned to commit hashes and run with read-only permissions; **Dependabot** proposes weekly updates for libraries and actions.
- **Repositories**: only Google Maven and Maven Central. JitPack is no longer used (it was only needed for uCrop, which was replaced by an own crop tool).


## Licences

Bunny Media Editor is licensed under the GNU General Public License, version 3 or later (see `LICENSE`).

The built-in stickers are images from [Noto Emoji](https://github.com/googlefonts/noto-emoji) by Google, taken from version `v2020-09-16-unicode13_1`, where the image resources are licensed under the Apache License, Version 2.0. They were rendered from SVG to 512 × 512 px WebP. Licence and details: `media-editor/photoEditor/src/main/assets/stickers/noto/`. (Newer versions of the Noto Emoji repository are under the SIL Open Font License; the stickers deliberately come from the earlier, Apache-licensed version.)

The fonts for texts are bundled, unchanged, from the [Google Fonts repository](https://github.com/google/fonts): Bebas Neue, Anton, Pacifico, Caveat, Playfair Display and Lobster under the SIL Open Font License 1.1, Permanent Marker under the Apache License 2.0. Licences and details: `media-editor/photoEditor/src/main/assets/fonts/`. They are never downloaded.
