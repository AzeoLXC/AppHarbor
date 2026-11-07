# AppHarbor

AppHarbor is an Android app inventory and package utility. It gives you a searchable view of installed apps, lets you inspect package metadata, and provides APK import and export tools. The interface is built with native Android views and uses Android's package APIs.

## What it can do

- Search installed apps by display name or package ID.
- Filter user, system, and disabled packages, and sort by name or last update.
- Inspect app version, install dates, requested permissions, and declared activities, services, receivers, and providers.
- Open an app or its Android settings page.
- Inspect and install one APK file at a time through Android's package installer.
- Export selected apps' base and split APK files to a ZIP archive.
- Save package selections as local lists for later use.
- Run package actions with root access: enable, disable, force stop, clear app data, or uninstall for the current user.
- Open Android's Usage Access settings.

## Requirements

- Android 6.0 (API 23) or newer.
- Root access for root-only package actions. AppHarbor requests authorization from the device's `su` manager when an action is run.
- For builds: JDK 21 and Android SDK Platform 36 with Build Tools 36.0.0.

AppHarbor targets Android 15 (API 35) and compiles against Android 16 (API 36). It requests `QUERY_ALL_PACKAGES` to show a device-wide app inventory and `REQUEST_INSTALL_PACKAGES` for APK installs. Distribution platforms may apply additional package visibility or installation policies.

## Build

The project uses the Gradle Wrapper (Gradle 9.9.0). From the project root, run:

```bash
bash ./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Important behavior

- APK installation is handed to Android's package installer, which may display its own confirmation screens. AppHarbor can inspect one `.apk` file; app bundles and multi-APK install sets are not supported as inputs.
- APK export copies the base APK and any readable split APK files exposed by Android. It does not back up private app data.
- Root actions require an installed root manager and can change or remove apps and their data. AppHarbor asks for confirmation before running them.
- Saved lists and settings are stored locally on the device.
- AppHarbor does not provide private app-data backup or restore, app-op or component editors, tracker analysis, remote ADB access, network-policy controls, or usage-history reports.

## GitHub Actions

The Android build workflow validates the Gradle Wrapper, sets up JDK 21 and Android SDK Platform 36, then builds the debug APK on pushes, pull requests, or manual runs. It uploads the APK as the `appharbor-debug-apk` artifact for 14 days.

Pushes to `main` also create a GitHub Release. The release title and tag use `vYYYYMMDD-<6-character-commit-SHA>` (UTC), for example `v20261107-a1b2c3`. The release contains only the APK asset, named `AppHarbor-vYYYYMMDD-<6-character-commit-SHA>.apk`.

## License

AppHarbor is licensed under GPL-3.0. See [LICENSE](LICENSE) for the full text.
