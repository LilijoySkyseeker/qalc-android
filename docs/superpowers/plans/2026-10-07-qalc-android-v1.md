# qalc-android v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An Android app embedding libqalculate with live results, save-when-you-move-on history, a settings screen and currency updates, released via GitHub Releases for Obtainium.

**Architecture:** libqalculate + GMP + MPFR + libxml2 cross-compiled for arm64 with the NDK. A plain-C++ bridge core (host-testable against nixpkgs' libqalculate) is wrapped by a thin JNI layer and a Kotlin `Engine` running on one dedicated thread. Compose UI over a ViewModel that owns the live-evaluate and commit rules.

**Tech Stack:** Nix flake (androidenv), Gradle + Kotlin + Jetpack Compose (Material 3), DataStore, NDK/CMake, libqalculate 5.12.0.

**Spec:** `docs/superpowers/specs/2026-10-07-qalc-android-design.md`

## Global Constraints

- Package / applicationId: `io.github.lilijoyskyseeker.qalc` (permanent once released). App label: `Qalc`.
- `minSdk = 28`, ABI `arm64-v8a` only.
- Native versions, matching nixpkgs so host tests exercise the same engine: libqalculate 5.12.0, GMP 6.3.0, MPFR 4.2.2, libxml2 2.15.4.
- libqalculate configure flags: `--enable-compiled-definitions --without-libcurl --without-icu --without-gnuplot-call --disable-insecure --disable-nls --disable-textport --enable-static --disable-shared`.
  Compiled definitions replace the spec's "bundle data files in assets" (same offline outcome, no copy step).
- Live calculation timeout: 2000 ms. Exchange rates refreshed when older than 1 day.
- Licence: GPL-2.0-or-later.
- Gradle/AGP/Kotlin/Compose BOM: latest stable at execution time, pinned in `gradle/libs.versions.toml`.
- The signing key never enters the repo or the agent's environment.

## Review Focus

1. **Typing an assignment live** (`x := 5` passes through `x :=`): partial assignments must not define variables. Live preview skips expressions containing `:=` or `save(`; they evaluate only on commit. → Task 5 test.
2. **Runaway expression** (`factorial(100000000)`): times out, and the next keystroke is not blocked. → Task 2 and Task 5 tests.
3. **Out-of-order results while typing fast**: a late result for older input must not overwrite the newer one. → Task 5 test.
4. **App killed mid-write**: a truncated last history line is skipped; earlier entries load. → Task 4 test.
5. **Failed or partial rate download**: the existing rate file stays intact. → Task 8 test.

---

### Task 1: Toolchain and native dependencies

**Files:**
- Create: `flake.nix`, `.envrc` (`use flake`), `.gitignore`, `LICENSE` (GPL-2.0 text)
- Create: `native/versions.env` (versions, URLs, sha256s), `native/build-deps.sh`
- Output (gitignored): `native/prebuilt/arm64-v8a/{include,lib}`

**Interfaces:**
- Produces: static libs `libqalculate.a libmpfr.a libgmp.a libxml2.a` plus headers in `native/prebuilt/arm64-v8a/`. Env `ANDROID_NDK_ROOT` and `ANDROID_HOME` from the dev shell.

- [ ] **Step 1: Write `flake.nix`**: devShell with JDK 17, `androidenv.composeAndroidPackages` (platform 35 or latest, build-tools, NDK, cmake), gradle, plus host tools `cmake pkg-config libqalculate gmp mpfr` for Task 2. nixpkgs config: `allowUnfree = true; android_sdk.accept_license = true;`. Export `ANDROID_HOME`, `ANDROID_NDK_ROOT`.
- [ ] **Step 2: Verify the shell.** Run `direnv allow && direnv exec . sh -c 'ls $ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android28-clang && pkg-config --modversion libqalculate'`. Expected: the path, then `5.12.0`.
- [ ] **Step 3: Write `native/build-deps.sh`**: download release tarballs (GMP and MPFR from gnu.org, libxml2 from download.gnome.org, libqalculate from `github.com/Qalculate/libqalculate/releases/download/v5.12.0/libqalculate-5.12.0.tar.gz`), verify sha256 from `versions.env` (record each with `nix-prefetch-url`), and build in order GMP → MPFR → libxml2 → libqalculate. Use autotools with `--host=aarch64-linux-android`, `CC/CXX` set to the NDK API-28 clang wrappers, `--prefix=native/prebuilt/arm64-v8a`, static only. libxml2: `--without-python --without-lzma --without-zlib --without-icu`, or its CMake build with the NDK toolchain file if autotools fails. libqalculate uses the Global Constraints flags plus `PKG_CONFIG_LIBDIR` pointing at the prefix. The script is idempotent: it skips a library whose `.a` exists.
- [ ] **Step 4: Verify.** Run `direnv exec . native/build-deps.sh && file native/prebuilt/arm64-v8a/lib/*.a | head`. Expected: all four `.a` exist; `readelf -h` on a member shows `AArch64`.
- [ ] **Step 5: Commit** (`build: nix toolchain and native dependency build`).

### Task 2: Bridge core (plain C++, host-tested)

**Files:**
- Create: `native/bridge/bridge_core.h`, `native/bridge/bridge_core.cpp`
- Create: `native/bridge/CMakeLists.txt` (host build: library + `bridge_test`, linked via `pkg-config libqalculate`)
- Create: `native/bridge/tests/bridge_test.cpp` (plain asserts, exits non-zero on failure), `native/bridge/tests/fixtures/eurofxref-daily.xml`

**Interfaces (Produces):**
```cpp
struct CalcResult { std::string text, parsed; std::vector<std::string> messages; bool ok, aborted; };
struct EngineSettings { int angleUnit; int approximation; int precision; int fractionFormat; int autoConversion; };
  // values are libqalculate enum ints: AngleUnit, ApproximationMode, NumberFractionFormat, AutoPostConversion
struct RateSource { std::string url, path; };
void engine_init(const std::string& userDir);   // setenv QALCULATE_USER_DIR, new Calculator, loadGlobalDefinitions,
                                                 // loadLocalDefinitions, loadExchangeRates, exchange-rate warnings on
CalcResult engine_calculate(const std::string& expr, int timeoutMs);
void engine_abort();                             // safe to call from any thread
void engine_apply(const EngineSettings&);
bool engine_save_definitions();
std::vector<RateSource> engine_rate_sources();   // getExchangeRatesUrl(i)/getExchangeRatesFileName(i), i = 1..4
bool engine_reload_rates();
```
`ok` = no `MESSAGE_ERROR` message and not aborted. `messages` holds all errors and warnings. Print options: `use_unicode_signs = true`. Defaults before any `engine_apply`: radians, `APPROXIMATION_TRY_EXACT`, precision 10, `FRACTION_DECIMAL`, `POST_CONVERSION_OPTIMAL`.

- [ ] **Step 1: Write failing tests** in `bridge_test.cpp`. Init with a fresh temp dir. Then, with `approximation = APPROXIMATION_APPROXIMATE`:
  - `"5 ft + 30 cm to in"` → text `"71.81102362 in"`, ok
  - `"1.6 m to ft"` → `"5 ft + 2.992125984 in"`
  - `"5 ft 3.5 in to cm"` → `"161.29 cm"`
  - `"sqrt(2)"` → `"1.414213562"`
  - `"sqrt("` → ok false, messages non-empty
  - `"10 / 0"` → ok true, messages contain `"Division by zero."`
  - `"factorial(100000000)"` with timeout 200 → aborted true, returns in < 2 s
  - after apply `APPROXIMATION_EXACT` + `FRACTION_FRACTIONAL`: `"1/3 + 1/4"` → `"7/12"`
  - `engine_rate_sources()` → 4 entries, each url starts with `https://` and path starts with userDir
  - copy the fixture (ECB XML with `USD rate="1.1"`) to source 1's path, `engine_reload_rates()`, then `"1 EUR to USD"` → `"1.1 USD"`
  - `"y := 7"`, then `engine_save_definitions()`, re-init in the same dir, then `"2y"` → `"14"`
- [ ] **Step 2: Run** `direnv exec . sh -c 'cmake -S native/bridge -B build/host && cmake --build build/host && build/host/bridge_test'`. Expected: build fails (no implementation).
- [ ] **Step 3: Implement** `bridge_core.cpp` against the interface above. Use `CALCULATOR->calculateAndPrint(expr, timeoutMs, eo, po, &parsed)` and collect messages via `message()`/`nextMessage()`. Clear messages before each calculation. Hold `EvaluationOptions`/`PrintOptions` as file statics updated by `engine_apply`. If the fixture's exact output differs only in formatting (e.g. `1.1 USD` vs `$1.10`), pin what the engine prints and note it in the commit.
- [ ] **Step 4: Run** the Step 2 command. Expected: all pass.
- [ ] **Step 5: Commit** (`feat: host-tested libqalculate bridge core`).

### Task 3: Android app skeleton, JNI, Engine (milestone 1: engine on the phone)

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, Gradle wrapper, `app/build.gradle.kts` (`abiFilters += "arm64-v8a"`, `externalNativeBuild.cmake`)
- Create: `app/src/main/AndroidManifest.xml` (INTERNET permission, label `Qalc`)
- Create: `app/src/main/cpp/CMakeLists.txt` (builds `qalcbridge` from `native/bridge/bridge_core.cpp` + `jni_bridge.cpp`, links the prebuilt `.a` files and `log`), `app/src/main/cpp/jni_bridge.cpp`
- Create: `app/src/main/java/io/github/lilijoyskyseeker/qalc/engine/{CalcResult.kt,Calculator.kt,Engine.kt}`, `.../MainActivity.kt`

**Interfaces:**
- Consumes: Task 2 `engine_*` functions; Task 1 prebuilt libs.
- Produces:
```kotlin
data class CalcResult(val text: String, val parsed: String, val messages: List<String>, val ok: Boolean, val aborted: Boolean)
data class RateSource(val url: String, val path: String)
interface Calculator { suspend fun calculate(expr: String): CalcResult; fun abort() }
class Engine(userDir: File) : Calculator {
  suspend fun apply(s: EngineSettings); suspend fun saveDefinitions(): Boolean
  suspend fun rateSources(): List<RateSource>; suspend fun reloadRates(): Boolean }
data class EngineSettings(val angleUnit: Int, val approximation: Int, val precision: Int, val fractionFormat: Int, val autoConversion: Int)
```
All `Engine` suspend calls run on one single-thread dispatcher (`Executors.newSingleThreadExecutor().asCoroutineDispatcher()`). `abort()` calls through directly. `calculate` uses a 2000 ms timeout. JNI builds `CalcResult` with `NewObject`.

- [ ] **Step 1: Scaffold the project**, with `MainActivity` showing `Engine.calculate("5 ft + 30 cm to in").text` in a Compose `Text`. Engine user dir: `filesDir/qalculate`.
- [ ] **Step 2: Build.** Run `direnv exec . ./gradlew assembleDebug`. Expected: `BUILD SUCCESSFUL`; `unzip -l app/build/outputs/apk/debug/app-debug.apk | grep arm64-v8a/libqalcbridge.so` matches.
- [ ] **Step 3: Owner check on phone.** Send the APK to the owner. Expected on screen: `71.81102362 in`.
- [ ] **Step 4: Commit** (`feat: Android skeleton running libqalculate on device`).

### Task 4: History store and copy formatting

**Files:**
- Create: `app/src/main/java/.../history/HistoryStore.kt`, `.../history/HistoryFormat.kt`
- Test: `app/src/test/java/.../history/HistoryStoreTest.kt`, `HistoryFormatTest.kt`

**Interfaces (Produces):**
```kotlin
data class HistoryEntry(val t: Long, val expr: String, val result: String)
class HistoryStore(file: File) { fun load(): List<HistoryEntry>; fun append(e: HistoryEntry); fun clear() }
object HistoryFormat { fun line(e: HistoryEntry): String /* "expr = result" */; fun lines(es: List<HistoryEntry>): String /* joined by "\n" */ }
```
Format: one JSON object per line, `{"t":..,"expr":..,"result":..}`, using `org.json` (on Android; add `org.json:json` as testImplementation for JVM tests).

- [ ] **Step 1: Write failing tests:**
  - append 2, load → same 2 in order
  - expression with `"`, `\n`, `√`, `−` round-trips
  - file whose last line is truncated (`{"t":1,"ex`) → loads the earlier entries, no exception
  - missing file → empty list
  - `clear()` → load empty
  - `line(HistoryEntry(0,"5 ft + 30 cm to in","71.81102362 in"))` == `"5 ft + 30 cm to in = 71.81102362 in"`
  - `lines` of two entries is joined by exactly one `\n` with no trailing newline
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*history*'`. Expected: FAIL.
- [ ] **Step 3: Implement.** `append` opens in append mode and writes the line plus `\n` in one write call.
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** (`feat: history store and copy formatting`).

### Task 5: CalcViewModel (live evaluation + commit rule)

**Files:**
- Create: `app/src/main/java/.../calc/CalcViewModel.kt`
- Test: `app/src/test/java/.../calc/CalcViewModelTest.kt` (fake `Calculator`, `kotlinx-coroutines-test`, temp-file `HistoryStore`)

**Interfaces:**
- Consumes: `Calculator`, `CalcResult` (Task 3), `HistoryStore`, `HistoryEntry` (Task 4).
- Produces:
```kotlin
enum class CommitReason { Enter, Clear, Background }
data class UiState(val input: String, val live: CalcResult?, val history: List<HistoryEntry>)
class CalcViewModel(calc: Calculator, store: HistoryStore, clock: () -> Long) : ViewModel() {
  val state: StateFlow<UiState>
  fun onInput(text: String); fun commit(reason: CommitReason); fun clearHistory() }
```
Rules:
- `onInput` cancels the running live job and calls `calc.abort()`, then starts a new evaluation. Expressions containing `:=` or `save(` get no live evaluation (`live = null`).
- A result is applied only if the input is unchanged since that evaluation started.
- `commit`: no-op if input is blank. Otherwise evaluate fresh (this is where assignments run). Append if `ok && !aborted` and `(expr, text)` differs from the last entry. `Enter` and `Clear` reset input to `""`; `Background` keeps it.

- [ ] **Step 1: Write failing tests:**
  - commit Enter on a valid result → appended, input `""`
  - result with ok=false → not appended, input `""` on Clear
  - same expr+result twice → one entry
  - blank → nothing
  - Background → appended, input kept
  - `onInput("x :=")` → fake never receives a calculate call; commit → fake receives `"x := 5"` once
  - fake delays "1+" by 500 ms and returns "2" immediately → final `live.text` is from "1+2", and `abort()` was called
  - aborted result on commit → not appended
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*calc*'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** (`feat: live evaluation and commit rule`).

### Task 6: Main screen (milestone 2)

**Files:**
- Create: `app/src/main/java/.../ui/MainScreen.kt`
- Modify: `MainActivity.kt` (wire `Engine`, `HistoryStore(filesDir/"history.jsonl")`, `CalcViewModel`; lifecycle `ON_STOP` → `commit(Background)`)

**Interfaces:** Consumes `CalcViewModel`, `HistoryFormat`. Produces navigation to `SettingsScreen` (Task 7) via a top-bar icon.

- [ ] **Step 1: Implement the layout.** History `LazyColumn` (newest at bottom, auto-scroll on append) above. Input `TextField` at the bottom (single line, IME action Done → `commit(Enter)`, focus and keyboard on launch, trailing clear icon → `commit(Clear)`), with the live result or first message below it. Material 3 dynamic colours; follows the system theme.
- [ ] **Step 2: Implement the interactions.**
  - Tap an entry → insert `result` at the cursor using `TextFieldValue` selection.
  - Long-press → menu: copy result, copy expression, copy `HistoryFormat.line`. Long-press also offers "Select" to enter multi-select mode; there a top bar has "Copy" (`HistoryFormat.lines` of the selection) and "Cancel".
  - Copy uses `ClipboardManager`.
- [ ] **Step 3: Build** `./gradlew assembleDebug`. Expected: success.
- [ ] **Step 4: Owner check on phone.** Live result while typing `5 ft + 30 cm to in`; Enter saves it; switch apps mid-line and come back → the line is in history; tap inserts; each copy option pastes correctly into another app; `x := 5`, then `2x` → `10`.
- [ ] **Step 5: Commit** (`feat: main screen with history interactions`).

### Task 7: Settings (milestone 3)

**Files:**
- Create: `app/src/main/java/.../settings/Settings.kt`, `SettingsStore.kt`, `.../ui/SettingsScreen.kt`
- Test: `app/src/test/java/.../settings/SettingsTest.kt`
- Modify: `MainActivity.kt` (apply settings to `Engine` at start and on change; nav)

**Interfaces (Produces):**
```kotlin
enum class AngleUnit(val q: Int) { Radians(1), Degrees(2), Gradians(3) }
enum class Approximation(val q: Int) { Exact(0), TryExact(1), Approximate(2) }
enum class FractionDisplay(val q: Int) { Decimal(0), Fraction(2), Mixed(3) }
enum class AutoConversion(val q: Int) { None(0), OptimalSi(1), Base(2), Optimal(3) }
data class Settings(val angle: AngleUnit = AngleUnit.Radians, val approximation: Approximation = Approximation.TryExact,
  val precision: Int = 10, val fractions: FractionDisplay = FractionDisplay.Decimal, val autoConversion: AutoConversion = AutoConversion.Optimal) {
  fun toEngine(): EngineSettings }
class SettingsStore(context: Context) { val settings: Flow<Settings>; suspend fun update(f: (Settings) -> Settings) }
```
The `q` values are libqalculate 5.12.0 enum ints (checked against `includes.h`). Precision range: 2–100.

- [ ] **Step 1: Write failing test:** `toEngine()` of the defaults == `EngineSettings(1, 1, 10, 0, 3)`; Degrees + Mixed map to 2 and 3.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*settings*'`. Expected: FAIL.
- [ ] **Step 3: Implement** the store (Preferences DataStore) and a screen with one row per setting plus "Clear history" (confirm dialog → `CalcViewModel.clearHistory()`, which also clears the file).
- [ ] **Step 4: Run** the tests and `assembleDebug`. Expected: PASS, success.
- [ ] **Step 5: Owner check:** set fractions to Mixed, then `160 cm to ft` → `5 ft + (2 + 126/127) in`; degrees → `sin(90)` = `1`; settings survive an app restart.
- [ ] **Step 6: Commit** (`feat: settings screen`).

### Task 8: Currency updates (milestone 4)

**Files:**
- Create: `app/src/main/java/.../rates/RatesUpdater.kt`
- Test: `app/src/test/java/.../rates/RatesUpdaterTest.kt`
- Modify: `MainActivity.kt` (run the updater once on start, in the background)

**Interfaces:**
- Consumes: `Engine.rateSources()`, `Engine.reloadRates()`.
- Produces:
```kotlin
fun interface Fetcher { fun fetch(url: String): ByteArray }  // throws on failure; real impl: HttpURLConnection, 15 s timeouts
class RatesUpdater(sources: suspend () -> List<RateSource>, reload: suspend () -> Boolean, fetch: Fetcher, now: () -> Long) {
  suspend fun updateIfStale(): Boolean }  // true if anything was refreshed
```
Stale = source 1's file is missing or its mtime is older than 1 day. Each source downloads to `path.tmp` and is then renamed over `path`. Failures are per-source and don't stop the others. Calls `reload()` once if any source succeeded.

- [ ] **Step 1: Write failing tests:**
  - fresh file → fetch never called
  - missing file → all sources fetched, files written, reload called once
  - fetch throws for source 1 → the old file's bytes are unchanged, no `.tmp` left, the other sources still written
  - all fail → reload not called, returns false
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*rates*'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Owner check:** with network, `100 USD to EUR` gives a plausible number; in airplane mode on a fresh install, the result shows the engine's no-rates message and the app doesn't crash.
- [ ] **Step 6: Commit** (`feat: exchange rate updates`).

### Task 9: Release (milestone 5)

**Files:**
- Create: `release.sh`, `README.md`
- Modify: `app/build.gradle.kts` (release `signingConfig` from env `QALC_KEYSTORE`, `QALC_KEYSTORE_PASSWORD`, `QALC_KEY_ALIAS`, `QALC_KEY_PASSWORD`; `versionCode`/`versionName` from `release.sh` args)

- [ ] **Step 1: Write `release.sh <versionName> <versionCode>`.** It checks the four env vars are set, runs `native/build-deps.sh` and `./gradlew assembleRelease -PversionName=… -PversionCode=…`, then `gh release create v<versionName> app/build/outputs/apk/release/app-release.apk --title v<versionName> --notes ""`.
- [ ] **Step 2: Write `README.md`.** What the app is; install via Obtainium (add the repo URL); build from source (`direnv allow`, `native/build-deps.sh`, `./gradlew assembleDebug`); a one-time key setup block `keytool -genkeypair -v -keystore ~/qalc-release.jks -alias qalc -keyalg RSA -keysize 4096 -validity 36500` with "back this file up — losing it means users must reinstall"; licence line.
- [ ] **Step 3: Verify** without a key: `./release.sh 0.1.0 1` with the env vars unset exits non-zero with a message naming the missing variables.
- [ ] **Step 4: Commit** (`build: signed release script`), push, open the PR.
- [ ] **Step 5: Owner:** create the key, run `./release.sh 0.1.0 1`, and add the repo in Obtainium. Expected: Obtainium installs v0.1.0.
