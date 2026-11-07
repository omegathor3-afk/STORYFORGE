# STORYFORGE

> اصنع عوالمك… واترك القصة تتكلم  
> Create your worlds… and let the story speak.

STORYFORGE is an offline-first Android story authoring studio built with Kotlin and Jetpack Compose.

## Current implemented features

- Arabic-first dark fantasy interface with RTL support.
- Multiple story projects with local autosave.
- Scene editor for scene titles and narrative text.
- Multiple player choices per scene.
- Per-choice destination routing to another scene or a story ending.
- Story variables with integer initial values.
- Conditional choice visibility and choice effects.
- Undo / redo history (up to 40 editing snapshots).
- Character list management.
- Interactive story preview.
- JSON import/export through Android's document picker.
- Manual JSON backup and restore tools.
- Safe project deletion with confirmation.

## Choice rules

Conditions support one comparison, for example:

- `keys>=1`
- `trust<3`
- `alarm==0`

Effects support assignment and integer changes:

- `keys=0`
- `trust+=1`
- `alarm-=1`

Variable identifiers can contain Arabic or Latin letters, digits, and underscores. Keep the variable name consistent in its definition, condition, and effect. A blank condition means the choice is always available; a blank effect changes nothing.

## Build

The project uses Kotlin, Jetpack Compose, Android SDK 35, Java 17, and Gradle. GitHub Actions builds a debug APK on pushes to `main` and uploads it as the `STORYFORGE-debug-apk` artifact.

## Immersive play mode

- Full-screen cinematic story mode with typewriter narration, scene artwork, dialogue cards, incoming-call interaction cues, haptic choice feedback, variable display, and per-scene looping audio.
- Each scene can use its own imported image and audio assets. The immersive player uses the same branches, conditions, and effects as the studio preview.
- STORYFORGE has a custom purple storybook launcher icon and animated branded intro. Each story also opens with its own title-screen intro using the project's cover art and description.

## Export a single game package

- Export one story as a portable `.storygame` ZIP containing a versioned `game-manifest.json`, one project JSON payload, and its referenced image/audio assets.
- Package metadata preserves the story title, description, Arabic language, scene count, and a cover-art reference for downstream Android player packaging.
- This is a portable game-content bundle, **not yet a separately installable APK**. The APK build pipeline for arbitrary user-created stories remains a separate milestone.

## Standalone Android Player\n\n- A separate Android application module (`:player`) is built as its own APK with application ID `com.storyforge.player`.\n- Install the player APK on an Android device, export a `.storygame` package from STORYFORGE, then import that package inside the player.\n- The player reads the versioned manifest and story JSON, extracts validated story media, displays a title screen, plays scenes and dialogue, evaluates choice conditions/effects, and supports restart.\n- This first player APK is a reusable runtime. Per-story custom launcher icons and cloud-generated uniquely branded APKs are future work; this module does not yet compile a unique APK from inside the phone.\n\n## Current limitations

This is an actively developed project, not a final release. The visual node graph, richer dialogue/asset management, persistent playthrough saves, audio controls, full localization, and automated/device-level tests are still planned. The current preview state is session-only and resets when the preview is restarted.
