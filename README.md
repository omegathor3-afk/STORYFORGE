# STORYFORGE

> اصنع عوالمك… واترك القصة تتكلم  
> Create your worlds… and let the story speak.

STORYFORGE is an offline-first Android story authoring studio built with Kotlin and Jetpack Compose.

## Implemented features

- Arabic-first dark fantasy interface with RTL support.
- Multiple story projects with local autosave.
- Scene editor for narrative text, dialogue, characters, and branching choices.
- Story variables, conditional choice visibility, and choice effects.
- Undo / redo history, interactive preview, and project backup tools.
- Immersive Player with scene artwork, dialogue, typewriter narration, audio, and choice feedback.
- Separate Player and Academy Android application modules.
- Portable `.storygame` export containing a versioned manifest, project data, and referenced media.

## Standalone Android games

A GitHub Actions workflow can package one exported `.storygame` into its own installable Android APK:

- The story bundle is embedded and loaded automatically at launch.
- Each generated game can have its own Android application ID and launcher name.
- An optional square PNG icon can be supplied.
- The workflow validates the package and produces a SHA-256 checksum and build metadata.

See the complete Arabic walkthrough: [docs/STANDALONE_APK.md](docs/STANDALONE_APK.md).

**Current limitation:** the workflow currently produces a Debug APK for testing and direct installation. A signed Release build for Google Play distribution is a separate milestone. The APK generation currently runs through GitHub Actions; an in-app one-tap cloud builder is not yet connected.

## Build and CI

The project uses Kotlin, Jetpack Compose, Android SDK 35, Java 17, and Gradle. GitHub Actions runs automated story-engine and Android build checks and publishes APK artifacts.

## Development status

STORYFORGE is actively developed. Some advanced authoring workflows, expanded localization, device-level automated tests, and secure Release signing remain future work.
