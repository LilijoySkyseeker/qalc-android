# qalc-android — design

Date: 2026-10-07 · Status: draft for owner review

## Goal

An Android calculator that embeds the real libqalculate engine, so the full
power of Qalculate is on the owner's phone. The driving use is construction
work in a mixed metric/imperial setting: typing quantities with units
(`5 ft 3.5 in + 30 cm to mm`) and getting correct, readable answers,
offline, with every small calculation kept in history and easy to copy.

Success: the owner uses it on the job instead of the existing Android apps,
and it is installed and updated from a public GitHub repo via Obtainium.

## Scope

**v1 (engine-complete):** everything libqalculate evaluates from typed input
works — units, currency, algebra/`solve()`, calculus, matrices, bases and
bitwise, dates, datasets (`atom(Fe)`), uncertainty/intervals, user
definitions. One main screen plus a settings screen.

**After v1, one at a time, in this order:**
1. Autocomplete and inline suggestions (first item, confirmed by owner).
2. Further desktop-parity features (plotting, RPN, unit/function/variable
   browsers, base/calendar/percentage tools) — each only when the owner
   finds they want it on the phone. Each gets its own short design.

**Non-goals:** iOS/desktop, sync with desktop Qalculate, F-Droid, a
calculator keypad, 32-bit or x86 builds.

## Architecture

Native Android app in Kotlin with Jetpack Compose. libqalculate and its
dependencies are cross-compiled with the Android NDK and called through a
thin JNI bridge.

```
Compose UI ──► ViewModel ──► Engine (Kotlin wrapper) ──JNI──► bridge.cpp ──► libqalculate
                   │                                                         (GMP, MPFR, libxml2)
                   ├──► HistoryStore (file)
                   ├──► SettingsStore (DataStore)
                   └──► RatesUpdater (HTTP → rate files)
```

### Units

**Native engine (`app/src/main/cpp/`)**
- libqalculate, GMP, MPFR, libxml2 built for `arm64-v8a` by a pinned build
  script (`native/build-deps.sh`: fixed versions, checksummed tarballs),
  output to `native/prebuilt/`. Rerun only when upgrading.
- iconv comes from Android's libc (API 28+), so `minSdk = 28`.
- No libcurl, no ICU.

**Bridge (`bridge.cpp`, `Engine.kt`)** — the only code that touches
libqalculate. Small, stable surface:
- `init(dataDir)` — create the Calculator, load definitions and
  exchange-rate files.
- `calculate(expr, timeoutMs) → Result{ text, parsedExpr, messages[], ok }`
  — evaluate and format with current settings; aborts on timeout.
- `abort()` — cancel the running calculation.
- `applySettings(Settings)` — map settings onto evaluation/print options.
- `saveDefinitions()` — persist user variables/functions via
  libqalculate's own definitions file.
- `reloadExchangeRates()`.
- (post-v1) `suggest(prefix) → [Name{ name, title, kind }]` for autocomplete.

All calls run on a single dedicated engine thread (libqalculate's
Calculator is a global, not thread-safe).

**Data files** — libqalculate is built with `--enable-compiled-definitions`,
so its units, functions and datasets are compiled into the library; nothing
is copied at runtime. (Revised during planning; replaces bundling the XML
files as assets.)

**Currency (`RatesUpdater.kt`)** — the bridge asks the engine for its own
rate-source URLs and file paths (`getExchangeRatesUrl/FileName`), Kotlin
downloads each into place, then calls `reloadExchangeRates()`. Nothing about
the sources is hardcoded in the app. Updates when rates are older than one day and
the network is available; otherwise uses the last downloaded rates. Before
the first successful download, currency conversions show the engine's
"no exchange rates" error. Results computed with stale rates show the engine's own
staleness message.
Fallback if the files can't be matched faithfully: cross-compile libcurl
and let libqalculate fetch itself.

### Main screen

- History list above, scrolling; input line at the bottom with the live
  result beneath it. System keyboard opens on launch. Material 3, follows the
  system dark/light theme.
- **Live result:** each edit cancels any running calculation and starts a
  new one (timeout ~2 s; on timeout show "taking too long" and don't save).
  Errors/warnings show in place of the result, without blocking typing.
  Expressions containing `:=` or `save(` are not evaluated live (typing
  `x := 5` would otherwise define `x` at each partial step); they run on
  commit.
- **Commit rule ("save when you move on"):** the current line is appended to
  history when the user presses Enter, clears the line, or the app goes to
  the background — only if it evaluated successfully and differs from the
  last entry. Enter also clears the input for the next line.
- **History interactions:**
  - Tap → insert that entry's result at the cursor.
  - Long-press → menu: copy result / copy expression / copy
    `expr = result`.
  - Multi-select → copy selected entries as plain text, one
    `expr = result` per line.
- **Storage:** append-only file in app storage, one JSON object per line
  (`{t, expr, result}`). "Clear history" in settings.

### Settings screen

Stored in Jetpack DataStore, applied to the engine at startup and on change:
- angle unit (rad / deg / gra)
- exact vs approximate
- precision (significant digits)
- fraction display (decimal / fraction / mixed, e.g. `5 3/8 in`)
- automatic unit conversion (none / optimal / base / SI)
- clear history

One-off conversions use the engine's inline `to …` syntax (`to ft in`,
`to hex`, `to fraction`); no extra UI.

### Autocomplete (first post-v1 item; design sketch)

While typing an identifier, a suggestion strip above the keyboard lists
matching units, functions and variables with their titles
(`ft — foot`, `in — inch`), drawn from the engine's own name lists via
`suggest()`. Tapping a suggestion completes the word (functions get `(`).
Detailed design happens when we start it.

## Build, test, release

- **Toolchain:** Nix flake dev shell (JDK, Android SDK + NDK, Gradle).
  `direnv` `.envrc` → `use flake`.
- **Fast tests (host, seconds):**
  - The bridge's C++ logic is also built against nixpkgs' `libqalculate`
    on Linux and tested there.
  - Kotlin logic (commit rule, history store, settings mapping) is
    tested with plain JVM unit tests.
- **On-phone checks:** debug APKs sent to the owner through Remote Control;
  the owner installs and reports.
- **Release:** the owner creates the GitHub repo, a release signing key
  (once; the agent supplies the `keytool` command and never sees the key),
  and runs `./release.sh`, which builds a signed release APK and uploads it
  to GitHub Releases with `gh`. Obtainium tracks that repo's releases.
- **Licence:** GPL-2.0-or-later (required by libqalculate).

## Build order

1. **Engine on the phone (riskiest first):** cross-compile the deps; a
   bare app showing `5 ft + 30 cm to in` evaluated on the device.
2. Main screen: live result, commit rule, history and its copy interactions.
3. Settings screen.
4. Currency updates.
5. First signed release via Obtainium.
6. Autocomplete and inline suggestions.

## Parts and sources

| Part | Source |
|---|---|
| Full libqalculate engine, all typed features | Owner |
| System keyboard, no keypad | Owner |
| Live result + save-when-you-move-on history | Owner |
| Tap insert / long-press copy / multi-select copy | Owner |
| Settings screen + inline `to …` | Owner |
| Currency | Owner |
| Autocomplete as first post-v1 item | Owner |
| Public repo, Obtainium, GitHub Releases | Owner |
| GPL licence | libqalculate licence |
| One stable signing key | Android/Obtainium update rule |
| Calculation timeout/cancel ⚑ | Claude: live results must not hang |
| Single engine thread ⚑ | Claude: libqalculate global state |
| Kotlin rate downloads instead of libcurl ⚑ | Claude: avoid building curl+OpenSSL |
| Nix flake toolchain ⚑ | Claude: reproducible build on NixOS |
| Host-side tests via nixpkgs ⚑ | Claude: faster feedback cycle |

⚑ = added by Claude; open to deletion.

## Deliberately left out (add-back triggers)

- **Keyboard shortcut row** (`^ ( ) ×`) — reaching symbols on the system
  keyboard proves annoying.
- **`set` commands / quick toggles** — the owner keeps switching a mode.
- **History search** — scrolling history becomes painful.
- **32-bit / x86_64 builds** — an older phone, or emulator testing is needed.
- **CI releases** — manual releasing becomes a chore.
- **libcurl** — Kotlin rate downloads can't match libqalculate's sources.

## Risks

- **Cross-compiling GMP/MPFR/libxml2/libqalculate with the NDK** is the
  main unknown; build-order step 1 exists to retire it first.
- **Exchange-rate files** are libqalculate internals; asking the engine for
  its URLs and paths keeps the app in step with whatever version is pinned.
