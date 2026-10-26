# PPG Workshop Downloader

Android app for downloading People Playground Steam Workshop items without requiring the user to sign in. The app resolves Workshop metadata and attempts the anonymous Steam CM / SteamPipe path when Steam does not provide a direct `file_url`.

## Download location

Downloaded files are exported to `Downloads/ppg/<addon title> (<Workshop ID>)/`.

## Build

The GitHub Actions workflow `.github/workflows/build-apk.yml` builds the debug APK. Open the repository's **Actions** tab and download the `PPGWorkshopDownloader-debug-apk` artifact from a successful run.

## Access limitations

Anonymous access depends on Steam's response and each item's visibility/authorization. This app does not guarantee downloads for every Workshop item.

## Third-party code

The Steam protocol and Workshop download engine are adapted from [Apricityx/WorkshopAndroidDownloader](https://github.com/Apricityx/WorkshopAndroidDownloader), under Apache License 2.0. See `THIRD_PARTY_NOTICES.md` and `third_party/WorkshopOnAndroid-LICENSE`.
