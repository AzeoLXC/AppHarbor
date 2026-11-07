# AppHarbor

AppHarbor is an Android app inventory and package utility. It uses a compact, dark interface built from Android views and Android's public package APIs.

## Features

- Search installed packages by app name or package ID.
- Filter user, system, and disabled apps; sort by name or recent update.
- Inspect requested permissions and declared activities, services, receivers, and providers.
- Review a single APK file before asking Android to install it.
- Export selected installed apps' base and split APK files into a ZIP archive.
- Save local package lists for later selection.
- Open Android's app settings and launch an installed app.
- Run selected package operations through su on rooted devices.

## Platform behavior

AppHarbor targets Android 15 (API 35), compiles against Android 16 (API 36), and supports Android 6.0 (API 23) and newer.

The Android package installer handles APK installation and may show its own confirmation screens. AppHarbor's root actions ask the device's su manager for access. Without root, package disable, force-stop, data-clear, and uninstall actions won't be available to the app.

APK export includes the package's base APK and split APK files where Android exposes readable paths. It does not include private app data. APK inspection and installation support one .apk file at a time; app bundles and split-APK sets are not install inputs.

The app requests QUERY_ALL_PACKAGES to show a device-wide inventory. Android distribution channels may impose additional package visibility policies.

This first version covers core inventory and package operations. It does not include private app-data backup or restore, app-op and component editors, tracker analysis, remote ADB access, network-policy controls, or usage-history reports.

## Automated build

The Gradle Wrapper pins Gradle 9.9.0 and checks its SHA-256 before use. The GitHub Actions workflow validates the Wrapper, installs JDK 21 and Android SDK Platform 36, and builds the debug APK on pushes, pull requests, and manual runs. It uploads the APK as the appharbor-debug-apk workflow artifact.

## License

Choose and add a license before distributing this project. No third-party application code, resources, or artwork are included.
