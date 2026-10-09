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

Build: `./gradlew assembleDebug`. Tests: `./gradlew :photoEditor:testDebugUnitTest` (unit) and
`./gradlew :photoEditor:connectedDebugAndroidTest` (integration, needs a running emulator or device).

