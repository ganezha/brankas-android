# Brankas
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)

A local-first Android vault for private photos, videos, and notes. The interface uses a restrained charcoal and warm-gold palette, with a custom safe icon, PIN access, optional biometric unlock, and a recovery code.

## Build the debug APK

Requirements: JDK 17 and Android SDK Platform 35 / Build Tools 35.0.0.

On Windows, from this folder run:

```powershell
.\gradlew.bat assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Build the optimized release APK

Run:

```powershell
.\gradlew.bat assembleRelease
```

The release variant enables R8 code shrinking, obfuscation, and resource shrinking. Without a local signing configuration, Gradle writes an **unsigned** APK to `app/build/outputs/apk/release/app-release-unsigned.apk`; it is suitable for inspecting the optimized build, but it cannot be published or installed as a signed update. For distribution, sign it through Android Studio's **Generate Signed Bundle / APK** flow. Keep the keystore and passwords outside the repository; local signing-property files and common keystore formats are ignored by `.gitignore`.

## Security model

- Media is selected through Android's document picker, encrypted with AES-256-GCM, authenticated, and hash-verified before the app asks Android to remove the source.
- Some Android document providers do not allow deletion. In that case the encrypted item is kept in Brankas, the original is left untouched, and the app reports that the move did not finish.
- Restore opens Android's destination picker with the original filename. The written file is read back and verified before its item is removed from the vault index.
- Each note body is stored in its own authenticated encrypted file. The encrypted index contains only item IDs, titles, media types, and creation times, so opening the vault does not decrypt every note body. Existing vaults with inline note bodies are migrated in a crash-safe order when first opened.
- A PIN-derived key wraps the vault key. A one-time recovery code provides a second way to unwrap it and reset the PIN.
- Optional biometric unlock stores only a biometric-authenticated Android Keystore wrapping of the vault key.
- Android automatic backup is disabled; the app requests no broad media or storage permissions.
- Settings also provides user-initiated encrypted backup export and restore. The archive contains the already-encrypted vault files, restores with the same PIN or recovery code, and replaces the vault currently on the device.
- `FLAG_SECURE` blocks normal screenshots and screen recording while the app is open.

The recovery code is shown once during setup. Keep it somewhere safe outside the phone. Forgetting both the PIN and recovery code means the encrypted vault cannot be opened.

## Current scope

This is a local-only first release. It has no cloud sync, account, analytics, or server. It includes user-initiated encrypted backup export and restore; production signing is not included.

## License

This project is licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE) for the complete license text.
