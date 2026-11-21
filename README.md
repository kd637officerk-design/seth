# READY PLAYER 1

Android wrapper for the uploaded XI Hyper-Realistic Media Deck, with the supplied READY PLAYER 1 poster used as the launcher icon and Android media artwork.

## Build the APK with GitHub

1. Create a new GitHub repository.
2. Upload the **contents** of this folder to the repository root (the `.github` folder must be included).
3. Open **Actions** and select **Build READY PLAYER 1 APK**. It also runs automatically after a push to `main` or `master`.
4. When the run finishes, open it and download the `READY-PLAYER-1-APK` artifact.
5. Extract the ZIP and install `app-debug.apk` on your Android phone. Allow installation from the browser/files app if Android asks.

## Included

- App name: **READY PLAYER 1**
- Supplied poster as launcher icon and media-session/notification artwork
- Android media session with Play, Pause, Previous, Next and Stop actions
- Media-style playback notification / lock-screen controls
- WebView wrapper for the uploaded player interface
- Android document picker for adding local audio/video files

## Notes

- The APK is built by GitHub Actions; this repository contains the Android source project, not a prebuilt APK.
- Media controls are connected to HTML audio/video elements and common player buttons. The exact next/previous behavior depends on the web player exposing working next/previous controls.
- GitHub's debug APK is suitable for personal testing. For Play Store distribution, configure a release signing key and build a signed release APK/AAB.
