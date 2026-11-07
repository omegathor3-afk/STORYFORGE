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

## Current limitations

This is an actively developed project, not a final release. The visual node graph, richer dialogue/asset management, persistent playthrough saves, audio controls, full localization, and automated/device-level tests are still planned. The current preview state is session-only and resets when the preview is restarted.
