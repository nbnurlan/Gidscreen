<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/40c1564e-5df4-4c24-bfa7-5014e9673cd3

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
7. If you have already published your app in AI Studio, please [request upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) in Google Play Console.

## APK signing and updates

Production-branch builds require the GitHub Actions secret `DEBUG_KEYSTORE_BASE64`.
Set it to the Base64 encoding of the **original** `debug.keystore` used to sign the
installed app (alias `androiddebugkey`, store/key password `android`). Keep the
keystore backed up privately; never commit it or its Base64 representation.

Previous CI builds generated a random key on every run. If that original key was
not saved, an APK signed by a new key cannot update those installations. Do not
uninstall the existing app until its data is backed up and migration is agreed.
A new permanent key establishes a new installation lineage, not compatibility
with the old one. This workflow deliberately fails instead of silently changing keys.

Pull requests and the fix branch compile and run unit tests with a temporary
validation-only key and placeholder API configuration. They do not publish an
installable update artifact or consume the production Gemini secret.

Device checks before release:
- Open chat, select a new area, cancel: the previous chat must reappear.
- Rotate portrait to landscape and back, then capture near each screen edge.
- Send a follow-up on a slow connection: sending/clearing/selecting must stay
  disabled until success or failure, then become available again.
- Install the persistently signed APK over an older APK with that same key.

The Gemini key is still bundled into the APK. This change does not solve API-key
protection for public distribution; a separate backend/authentication design is needed.

## In-app update notification

On launch, the new app checks
`https://github.com/nbnurlan/Gidscreen/releases/latest/download/update.json`.
It offers a download only when the release's Android `versionCode` is greater
than the installed version. The dialog is translated into Uzbek, Russian and
English. "Later" dismisses it for this activity session (including rotation).
No network, no release, or an invalid manifest leaves the app usable without a
dialog. The download button opens the APK in the browser; Android still asks the
user to install it. There is no silent installation or background notification.

Existing APKs, including build #29, do not contain this check. Install this first
updated version manually; only subsequent releases can be announced by it.

### Publish a version from a phone

1. Apply the changes and commit them to `main`.
2. In GitHub, open **Settings → Secrets and variables → Actions**. Keep
   `GEMINI_API_KEY` and add `DEBUG_KEYSTORE_BASE64` with the original signing key.
   The workflow cannot recover a private key from a previously built APK.
3. For each later version, increase **both** `versionCode` and `versionName` in
   `app/build.gradle.kts`. Use a three-part name such as `1.2.2`.
4. Open **Actions → Build APK → Run workflow** on `main`. Leave
   **publish_release** unchecked to build and test without publishing. Download
   the **app-debug** artifact and test its `Gidscreen.apk` on the device.
5. When ready, run the workflow again with **publish_release** checked. A
   successful build and tests are required before publishing. The release
   contains `Gidscreen.apk` and `update.json`, with metadata extracted from the
   built APK's `output-metadata.json`. Existing release versions are not replaced;
   the version code must exceed all prior published update manifests.

The release URL currently targets `nbnurlan/Gidscreen`. Forks must change it in
both `AppUpdateChecker.kt` and `scripts/prepare_release.py` before publishing.

### Checks

```sh
python3 -m unittest discover -s scripts -p 'test_*.py' -v
./gradlew assembleDebug testDebugUnitTest --stacktrace
```

Android tests cover cancelling selection, rejecting busy chat submissions, and
update-manifest validation (new/equal/older version, malformed data, wrong app,
unsafe download URL). Python tests cover release packaging and version metadata.
Physical-device checks are still required for screen rotation, slow-network
chat behaviour, and updating an installation signed with the same key.
