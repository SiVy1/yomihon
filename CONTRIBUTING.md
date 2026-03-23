Looking to report a bug or request a feature? Start with the [README](./README.md) and the current [issues list](https://github.com/SiVy1/yomihon/issues).

---

Thanks for your interest in contributing to Yomihon!

# Code contributions

Pull requests are welcome.

If you want to work on an [open issue](https://github.com/SiVy1/yomihon/issues), leave a comment so others know it is being handled. You do not need an assignment first.

## Prerequisites

Before you start, please note that working knowledge of the following is required:

- Basic [Android development](https://developer.android.com/)
- [Kotlin](https://kotlinlang.org/)

### Tools

- [Android Studio](https://developer.android.com/studio)
- Emulator or phone with developer options enabled to test changes

## Getting help

- Use [GitHub issues](https://github.com/SiVy1/yomihon/issues) for bug reports and feature requests.
- Use pull request discussion for implementation-specific review and follow-up.

# Translations

Strings live in [`i18n/src/commonMain/moko-resources`](./i18n/src/commonMain/moko-resources). Update the base strings and the relevant locale files together when making translation changes.

# Forks

Forks are allowed as long as they comply with the [project LICENSE](./LICENSE).

When creating a fork, remember to:

- Avoid confusion with this app:
  - Change the app name
  - Change the app icon
  - Change or disable the [app update checker](./app/src/main/java/eu/kanade/tachiyomi/data/updater/AppUpdateChecker.kt)
- Avoid installation conflicts:
  - Change the `applicationId` in [`app/build.gradle.kts`](./app/build.gradle.kts)
- Avoid mixing analytics or crash reporting data:
  - If you use Firebase services, replace [`app/src/standard/google-services.json`](./app/src/standard/google-services.json) with your own
