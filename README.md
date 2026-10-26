# PPG Workshop Downloader

Android app prototype for downloading public People Playground Steam Workshop items to `Downloads/ppg`.

## Build

The GitHub Actions workflow in `.github/workflows/build-apk.yml` builds a debug APK. Open the **Actions** tab and select the workflow to run it.

## Current limitation

Steam Workshop does not expose a direct downloadable file URL for every item through its public details API. This prototype therefore does not yet support every workshop item. It is not a complete replacement for a Steam Workshop client.
