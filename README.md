# Qalc

An Android calculator built on [libqalculate](https://github.com/Qalculate/libqalculate),
the engine behind Qalculate!. Everything the engine understands works when
typed: units (`5 ft 3.5 in + 30 cm to mm`), currency, algebra (`solve(x^2 = 4)`),
calculus, matrices, bases, dates, datasets, uncertainty and your own variables
(`x := 5`).

- Results appear as you type, using your phone's normal keyboard.
- A line is saved to history when you move on: Enter, clearing the line,
  or leaving the app.
- Tap a history entry to insert its result. Long-press to copy the result,
  the expression or the whole line, or to select several entries and copy
  them as plain text.
- Settings: angle unit, exact/approximate, precision, fractions (including
  mixed fractions to the nearest 1/8, 1/16 or 1/32 — `1 m to ft` →
  `3 ft + (3 + 6/16) in`) and automatic unit conversion.
- Works offline. Exchange rates update once a day when there is a network.

Tip: write feet and inches as `5 ft 3.5 in` or `5'3.5"`. `5 ft 3 1/2 in` is
read as `5 ft × 3 × ½ in`.

## Install

Use [Obtainium](https://github.com/ImranR98/Obtainium): add this repository's
URL as an app source. Obtainium installs the APK from the latest GitHub
release and updates it when a new release appears. Requires Android 9 or
newer on a 64-bit ARM phone.

## Build from source

Needs Nix with flakes and direnv.

```sh
direnv allow
direnv exec . native/build-deps.sh        # cross-compiles GMP, MPFR, libxml2, libqalculate (once)
direnv exec . ./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
```

Tests:

```sh
direnv exec . sh -c 'cmake -S native/bridge -B build/host && cmake --build build/host && build/host/bridge_test'
direnv exec . ./gradlew testDebugUnitTest
```

## Releasing

One-time: create a signing key and keep it outside the repository. **Back it
up — every release must be signed with the same key, and losing it means
reinstalling the app (and losing history) to update.**

```sh
keytool -genkeypair -v -keystore ~/qalc-release.jks -alias qalc -keyalg RSA -keysize 4096 -validity 36500
```

Each release:

```sh
export QALC_KEYSTORE=~/qalc-release.jks QALC_KEY_ALIAS=qalc
read -rs QALC_KEYSTORE_PASSWORD; export QALC_KEYSTORE_PASSWORD QALC_KEY_PASSWORD=$QALC_KEYSTORE_PASSWORD
./release.sh 0.1.0 1        # versionName, versionCode (increase the code every release)
```

## Licence

GPL-2.0-or-later, as required by libqalculate. See [LICENSE](LICENSE).
