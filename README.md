# Pi Launcher

A free, simple Apple TV-style home screen for Android TV (made for LineageOS on Raspberry Pi 4).
https://github.com/osbensaid/PiLauncher/actions/runs/45670110284/artifacts/15050250157
## Features
- Grid of large 16:9 app tiles that grow when selected; the app name shows underneath
- Faded picture of the selected app across the background, like Apple TV's top shelf
- Clock and date
- Hold OK on an app: Move, Hide, App info, Uninstall
- Gear button (top right): wallpaper, hidden apps, reset order, Android settings
- Shows TV apps and sideloaded phone apps

## Build the APK without installing anything (GitHub)
1. Create a free GitHub account and a new repository.
2. Upload all files in this folder (keep the folder structure, including `.github`).
3. Open the **Actions** tab. The "Build APK" workflow runs automatically (or click **Run workflow**).
4. When it finishes (about 3-5 minutes), open the run and download **PiLauncher-apk**.
5. Unzip it to get `app-debug.apk`.

## Or build with Android Studio
Open this folder in Android Studio, let it sync, then **Build > Build APK(s)**.

## Install on the Pi
1. Copy `app-debug.apk` to a USB stick, or upload it somewhere and use Downloader.
2. Install it, then press Home and pick **Pi Launcher** > **Always**.
   (Or Settings > Apps > Default apps > Home app.)

## Moving apps
Hold OK on an app > **Move**. Use the arrow keys to move it, then press OK. The order is saved.

## Customise
- Number of columns: `COLUMNS` in `MainActivity.kt`
- Wallpaper colours: the `wallpapers` list in `MainActivity.kt`
- Tile corner rounding: `drawable/tile_bg.xml`
