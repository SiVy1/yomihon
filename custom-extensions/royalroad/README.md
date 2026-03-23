# Royal Road extension starter for Yomihon

This folder contains a ready-to-adapt starter for a text-native extension that scrapes Royal Road.

It is intentionally kept standalone so it does not affect the main app build.

## What is included

- `AndroidManifest.xml` with Tachiyomi/Yomihon extension metadata
- `RoyalRoadSourceFactory.kt` source factory entrypoint
- `RoyalRoadNovelSource.kt` ParsedHttpSource implementation

## Notes

- This starter targets text chapters and returns `TextPage` objects.
- Selectors on Royal Road can change over time. If the site layout changes, update CSS selectors in `RoyalRoadNovelSource.kt`.
- Respect Royal Road terms of service and robots/access policies.

## Build integration

Use this code in a dedicated extension APK project (the same pattern as Tachiyomi/Mihon extension repos):

1. Create an Android app module for the extension.
2. Copy the files from `src/main/kotlin/...` and `AndroidManifest.xml`.
3. Make sure your extension module depends on the extensions API that matches your app's loader (`extensions-lib 1.5` compatible).
4. Build and install/sign the extension APK.

## Package/class metadata

The manifest points to this factory class:

- `app.yomihon.extension.en.royalroad.RoyalRoadSourceFactory`

If you change package names, update both source files and the manifest metadata accordingly.