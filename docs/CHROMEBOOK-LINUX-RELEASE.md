# Build, sign, and release Almara from Chromebook Linux

This guide builds Almara V1 in ChromeOS's Debian Linux environment, signs the APK with a key you keep, verifies it, and attaches it to a GitHub Release. The signing key is permanent: protect it and reuse the same key for every future update. Android will reject an update signed with a different key. [1]

## 1. Turn on Linux (Crostini)

On your Chromebook, open **Settings → About ChromeOS → Developers → Linux development environment → Set up** and complete the prompts. Google says setup may take 10 minutes or more, and a school/work administrator may disable it. Linux apps share the Linux sandbox and files you share with Linux are accessible to those apps, so keep the signing key in your private Linux home directory and do not share it with untrusted Linux apps. [2]

Open the **Terminal** app and install the build prerequisites:

```bash
sudo apt update
sudo apt full-upgrade -y
sudo apt install -y openjdk-17-jdk git unzip zip curl ca-certificates
java -version
```

The Java output should show version 17. You do not need Android Studio or an Android emulator for this command-line release process. ChromeOS's Linux environment does not support the Android Emulator; test installation on a real Android device. [2]

## 2. Install Android SDK command-line tools

Download the **Command line tools only → Linux** ZIP from [Android Studio's official downloads page](https://developer.android.com/studio). Keep the download in ChromeOS Downloads and share it with Linux if ChromeOS asks. The Android SDK is made up of separately installed packages; `sdkmanager` is Google's supported command-line installer. [3]

In Terminal, extract the downloaded ZIP. Replace `<version>` with the digits in the filename you downloaded:

```bash
mkdir -p "$HOME/Android/Sdk/cmdline-tools"
unzip -q "$HOME/Downloads/commandlinetools-linux-<version>_latest.zip" -d /tmp/almara-android-cli
mkdir -p "$HOME/Android/Sdk/cmdline-tools/latest"
mv /tmp/almara-android-cli/cmdline-tools/* "$HOME/Android/Sdk/cmdline-tools/latest/"
```

Add the SDK tools to your shell path:

```bash
cat >> "$HOME/.bashrc" <<'EOF'
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
EOF
source "$HOME/.bashrc"
```

Install the versions used by this repository and accept the Android SDK licenses:

```bash
sdkmanager --sdk_root="$ANDROID_HOME" \
  "platform-tools" "platforms;android-35" "build-tools;35.0.0"
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses
```

The `apksigner` and `zipalign` programs used below are included in Android SDK Build Tools. Android documents both tools as command-line SDK components. [3] [4]

## 3. Get the reviewed source and create the permanent signing key

Clone the repository and apply the patch delivered with this task if these changes are not already in your checkout. If you download the patch into ChromeOS Downloads, share it with Linux first:

```bash
cd "$HOME"
git clone https://github.com/vijayarkananduri/Almara.git
cd Almara
git apply "$HOME/Downloads/Almara-v1-release-changes.patch"
git diff --check
git status --short
./gradlew --version
```

If you already have a checkout, use it instead of cloning a second copy. Review the changed files, then commit and push the reviewed source to `main` before making the signed APK:

```bash
git add .github/workflows/android.yml README.md app/src/main/java/com/almara/app/MainActivity.kt docs/CHROMEBOOK-LINUX-RELEASE.md gradle gradlew scripts/release-apk.sh
git commit -m "Add onboarding guide and signed release workflow"
git push origin main
```

Wait for the `Android APK` workflow on that commit to pass before signing. The app currently has `applicationId = "com.almara.app"`, `versionCode = 1`, and `versionName = "V1"`.

Create the keystore **outside** the repository. Use a strong password and keep an offline backup in a second secure location. Do not commit the `.jks` file, paste it into chat, upload it as a public release asset, or add it as a normal repository file. Almara's `.gitignore` already ignores `.jks` and `.keystore` files, but that is only a safety net.

```bash
mkdir -p "$HOME/.local/share/almara-signing"
chmod 700 "$HOME/.local/share/almara-signing"
keytool -genkeypair -v \
  -keystore "$HOME/.local/share/almara-signing/almara-release.jks" \
  -alias almara \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Vijayarka Nanduri, OU=Almara"
chmod 600 "$HOME/.local/share/almara-signing/almara-release.jks"
```

`keytool` prompts for the keystore and key passwords. Save them in your password manager. If this exact release key has already been created, do **not** make another one—use the original keystore and alias. Losing the app signing key can prevent future APK updates, because sideloaded APK distribution does not have Play App Signing to manage your key. [1]

## 4. Run tests, build, sign, and verify

From the repository root, run the prepared script:

```bash
ALMARA_KEYSTORE="$HOME/.local/share/almara-signing/almara-release.jks" \
ALMARA_KEY_ALIAS="almara" \
./scripts/release-apk.sh
```

The script asks for the two passwords without echoing them. It runs `:app:testDebugUnitTest`, `:app:assembleDebug`, and `:app:assembleRelease`; aligns the unsigned release APK; signs it; verifies the signature and prints the certificate details; and writes a SHA-256 checksum. `apksigner` invalidates signatures if you modify an APK afterward, so do not edit or re-align the signed output. Android's documented order is to align before signing and then verify the signature. [4]

The distributable file is:

```text
app/build/outputs/apk/release/Almara-v1.0.0.apk
```

The checksum is next to it as `Almara-v1.0.0.apk.sha256`. Share only the signed APK, not `app-release-unsigned.apk`, the aligned intermediate APK, the debug APK, or the keystore. Keep the SHA-256 value in your release notes or attach the checksum file.

Before public release, install the signed APK on a real Android phone and check first-run onboarding, the “Read the setup guide” link, the “Skip and use app” action, Settings → Help, and the developer profile link. A device that has a differently signed debug or test build installed may refuse an in-place update; uninstall that test build first if needed, knowing that uninstalling clears the app's local data. Future public updates must use the same release key. [1]

## 5. Publish v1.0.0 on GitHub

First push the reviewed source changes to `main` and wait for the `Android APK` workflow to pass. That workflow builds unsigned CI APKs for testing. Stable published releases no longer receive those unsigned assets automatically; only prereleases may receive the CI APKs. Do not choose the manual workflow option that publishes unsigned CI APKs for the stable v1 release.

On GitHub, open the Almara repository, choose **Releases → Draft a new release**, create the tag **`v1.0.0`** from `main`, set the title to **Almara v1.0.0**, and describe the tested features and known limitations. Attach `Almara-v1.0.0.apk` and, optionally, `Almara-v1.0.0.apk.sha256`. Confirm the assets are the signed build and not the unsigned or debug APK, then publish. GitHub Releases associate downloadable binary assets with a Git tag; a draft lets you review the release and its files before publishing. [5]

The repository already has `v1.0.0-rc.1`, `v1.0.0-rc.2`, and `v1.0.0-rc.3`; `v1.0.0` is a distinct tag and is the intended stable tag for this release. Do not reuse or move a published tag. The local signing helper is intentionally not configured with any real password or keystore. You create and retain those on your Chromebook.

## References

[1]: https://developer.android.com/studio/publish/app-signing "Sign your app"
[2]: https://support.google.com/chromebook/answer/9145439?hl=en "Set up Linux on your Chromebook"
[3]: https://developer.android.com/studio/command-line "Android command-line tools"
[4]: https://developer.android.com/tools/apksigner "apksigner and APK verification"
[5]: https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository "Managing GitHub releases"
