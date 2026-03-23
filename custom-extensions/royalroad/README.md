# Royal Road extension starter for Yomihon

This folder contains a ready-to-adapt text-native extension that scrapes Royal Road.

It is included as its own Gradle module and can be built independently.

## What is included

- `AndroidManifest.xml` with Tachiyomi/Yomihon extension metadata
- `RoyalRoadSourceFactory.kt` source factory entrypoint
- `RoyalRoadNovelSource.kt` ParsedHttpSource implementation

## Notes

- This starter targets text chapters and returns `TextPage` objects.
- Selectors on Royal Road can change over time. If the site layout changes, update CSS selectors in `RoyalRoadNovelSource.kt`.
- Respect Royal Road terms of service and robots/access policies.

## Build

From repository root:

1. `./gradlew :custom-extensions:royalroad:assembleDebug`
2. Install generated APK from module outputs.

The module compiles against local projects:

- `:source-api`
- `:core:common`

## Package/class metadata

The manifest points to this factory class:

- `app.yomihon.extension.en.royalroad.RoyalRoadSourceFactory`

If you change package names, update both source files and the manifest metadata accordingly.