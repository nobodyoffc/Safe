# macOS Migration Plan — Safe Wallet

This document is a migration plan for porting the Android "Safe" cryptocurrency
wallet to a native macOS desktop application. It assesses the existing
codebase, proposes a target architecture, and breaks the work into concrete
phases.

The macOS app consumes the existing **`Freeverse/FC-JDK`** library as its
JVM crypto core. FC-JDK is already pure-JVM and Maven-packaged (no Android
types, no `androidx.*`), so no upstream consolidation is required to start
the port. A future consolidation of the three FC-AJDK copies is *deferred*
(see `/Users/liuchangyong/Desktop/Freeverse/FC-JDK/FC_CORE_SPLIT_PLAN.md`
for the parked plan).

---

## 1. Scope and Goals

Migrate the Android app (`com.fc.safe`) into a macOS desktop wallet with
feature parity for:

- Key management (create / import / export / backup, by phrase, privkey,
  prikey-cipher, pubkey, FID)
- Transaction building and signing (single-sig and multi-sig)
- Multi-signature identity and transaction flows
- Secret and TOTP storage
- Encrypt / decrypt / sign / verify / hash utilities
- QR code scan & generate (camera input → file/clipboard input on macOS)
- Avatar generation
- Password-based encrypted local storage with background-timeout lock

**The Android and macOS apps are independent products.** No database or
on-disk format migration between platforms. JSON import/export — the
app's own existing flow — is the only cross-platform data bridge. Golden
vectors are captured from that flow and used as both a regression suite
and the interop gate (§ Phase 5).

**Explicit non-goals for v1:** push notifications, live-camera QR
capture, Keychain-wrapped master key, Android-specific integrations
(CameraX, share-sheet), App Store distribution.

---

## 2. Current State Assessment

### 2.1 What `Freeverse/FC-JDK` gives us as-is

- Pure JVM, Maven-packaged (`pom.xml`), JDK 17 source/target
- No `import android.*` / `import androidx.*` anywhere in the tree
  (verified)
- Uses `bcprov-jdk18on:1.76` directly — no transitive BouncyCastle
  variant conflict to worry about (this was an Android-only concern)
- Includes key-management, crypto, data models, FID/FCH logic, QR
  encoding — the entirety of what the Safe UI depends on from FC-AJDK
- Also includes server-side packages (`server`, `fapi`, `fudp`,
  `managers`, `clients`, Netty, servlet-api, Apache HttpComponents) that
  the wallet doesn't need (§5 covers the classpath-bloat trade-off)

### 2.2 Two points of friction to plan around

**(a) Flat Java packages.** FC-JDK declares packages like `package utils;`,
`package core.crypto;` rather than reverse-domain `com.fc.fc_ajdk.*`. This
is legal but non-idiomatic and can collide with other jars that happen to
use the same top-level name. Phase 0 includes a smoke test for classpath
collisions once the UI-stack dependencies are added.

**(b) Divergence between FC-JDK and `Safe/FC-AJDK`.** The Safe Android
copy has 200 Java files; FC-JDK has 583 (superset including server
content). The Safe copy may have small Android-driven fixes that never
made it back to FC-JDK. Phase 0 includes a one-day diff of the files that
exist in both copies; any functional fixes in Safe/FC-AJDK that aren't
in FC-JDK get backported into FC-JDK before Phase 1 consumes it.

### 2.3 Android-specific code in `app/` (to be replaced)

- Storage: `HawkDB`, `MMKVDB`, `HawkToMMKVMigration` —
  MMKV / Hawk / `androidx.security:security-crypto` /
  `android-database-sqlcipher`
- All Activities/Fragments, `SafeApplication`, `BackgroundTimeoutManager`
- CameraX scanner pipeline
- Pervasive `Toast` / `ToastManager` usage
- Intent-based navigation (`startActivity(...)`, `putExtra(...)`) in
  100+ call sites — replaced with a navigation library (§3.1, Phase 2)
- 50 `.bak` files in `app/` — delete before porting

### 2.4 Architectural strengths that help migration

- `LocalDB` interface cleanly abstracts storage — only the
  implementation needs to change
- `DatabaseManager` centralizes DB lifecycle and password-context
  switching
- `FcEntity.toJson()` convention for passing data decouples UI from the
  model layer — and doubles as the cross-platform data format (§1)
- `ConfigureManager` is the single entry point for app settings

---

## 3. Target Architecture

### 3.1 Stack decision — mandatory 2-day spike

| Option | Pros | Cons | Verdict |
|---|---|---|---|
| **Compose Multiplatform Desktop (Kotlin)** | Modern declarative UI; good native macOS feel; seamless Java interop with FC-JDK; only option with a credible iOS path later | Kotlin ramp-up (~1–2 weeks); packaging newer than JavaFX | **Primary candidate.** Best UX + only iOS-compatible choice. |
| **JavaFX + JDK 17+** | Zero language cost; mature tooling | Dated UX; `jpackage` + FX runtime bundling friction; weaker native-macOS feel; no iOS path | **Fallback** if Compose Desktop packaging misbehaves in the spike. |
| Swing | No language cost; bundled with JDK | Dated UX | Only if both above fail. |
| Electron / Tauri | — | Wallet-grade attack surface; crypto re-implementation | **Rejected.** |
| SwiftUI + JNI to FC-JDK | Best native feel | Swift↔JVM bridge at a security boundary = debugging nightmare | **Rejected.** |

**Phase 0 spike (2 days, non-negotiable):**
- Day 1: Compose Multiplatform Desktop hello-world → `jpackage` →
  Developer-ID signed and notarized `.dmg` on target macOS.
- Day 2: same loop with JavaFX.
- Pick the one whose end-to-end pipeline didn't fight you.

### 3.2 Proposed module layout

```
SafeForMac/
├── app-desktop/       (Compose Desktop or JavaFX UI; entry point;
│                       depends on Freeverse/FC-JDK via Maven)
├── platform-macos/    (macOS-specific glue: SqliteDB, DesktopAppPaths,
│                       logback config, lock-detection JNA bridge,
│                       clipboard/drag-drop QR, snackbar impl)
└── (no `fc-core` module — FC-JDK is an external dependency)
```

FC-JDK is consumed either via:
- JitPack (FC-JDK's `pom.xml` already configures jitpack.io as a repo), or
- a Maven `<relativePath>` / Gradle `includeBuild` pointer to
  `/Users/liuchangyong/Desktop/Freeverse/FC-JDK` during development.

Recommendation: `includeBuild` during development so fixes flow upstream
in one commit; switch to a pinned Maven version for release builds.

### 3.3 Dependency replacements

| Android dep | macOS replacement |
|---|---|
| MMKV / Hawk | **SQLite (via `sqlite-jdbc`)** behind existing `LocalDB`, values encrypted using the app's `EccAes256K1P7` with a password-derived key — no SQLCipher (see 3.4) |
| `androidx.security:security-crypto` | v1: none (re-derive from typed password via Argon2, same as current cold-start flow). v2 optional: macOS Keychain via JNA. |
| `zxing-android-embedded` (scan) | ZXing core + image-file decode + clipboard-image decode. Live camera deferred (see Phase 3). |
| Android `Bitmap` in `ImageUtils` / `AvatarMaker` | FC-JDK already uses `BufferedImage` + `ImageIO` (Android `Bitmap` was stripped when FC-JDK was carved out). Consume FC-JDK's JVM versions directly. |
| CameraX | Out of scope for v1. |
| Coil | JavaFX `Image` / Compose Desktop `loadImageBitmap` — no async network loads required for current usage |
| `BackgroundTimeoutManager` | Multi-signal lock: window focus-loss **plus** screen lock **plus** system sleep (§3.4). |
| `Toast` / `ToastManager` | Non-modal snackbar / status-strip component; keep the `ToastManager` API stable so call sites don't rewrite |
| Intent-based navigation | A single `Navigator` stack with typed screen descriptors. JavaFX: `javafx-weaver` or similar. Compose: Voyager or Decompose. **Not hand-rolled** — 50+ screens is too many for ad hoc. |
| `android.util.Log` / `TimberLogger` | SLF4J + `logback-classic` with a redacting pattern layout (§3.5). Replace the current `slf4j-nop` (silent) binding. |
| `com.android.tools:desugar_jdk_libs` | Dropped — JVM 17 floor |
| `kotlinx-coroutines-android` | `kotlinx-coroutines-core` (+ `-swing` or `-javafx` for UI dispatch); only if coroutines are retained |

### 3.4 Storage model

- Replace `MMKVDB` with `SqliteDB : LocalDB` in `platform-macos`
- Per-password database context → one SQLite file per context under
  `~/Library/Application Support/com.fc.safe/db/{passwordHashPrefix}.sqlite`
- Values stored as JSON (same `FcEntity.toJson()` convention) encrypted
  with a key derived from the user password via FC-JDK's existing
  Argon2/KDF pipeline
- `DatabaseManager`'s context-switching semantics stay identical so
  higher layers are untouched
- No Hawk → MMKV migration code (macOS is a fresh install; Android data
  only crosses via JSON export/import)

**Trade-off, documented now.** Values are opaque ciphertext to SQLite.
Fine for the current KV access pattern; blocks SQL-side filtering over
value fields. If a future feature needs search, either move to
SQLCipher + plaintext values or add searchable plaintext columns
alongside encrypted payloads. Pick deliberately; don't add a column
reactively.

### 3.5 Background-lock detection — more than focus-loss

Android's `BackgroundTimeoutManager` locks the wallet on background.
macOS has no single equivalent — watch several signals:

| Signal | macOS mechanism | Required |
|---|---|---|
| Window focus-loss / app hidden | JavaFX `Stage.focusedProperty` / Compose `WindowState` | Yes |
| Screen locked | `CGSessionCopyCurrentDictionary` via JNA, or `NSDistributedNotificationCenter` observing `com.apple.screenIsLocked` | **Yes — critical for a wallet** |
| System sleep | `NSWorkspace` `NSWorkspaceWillSleepNotification` via JNA | Yes |
| Idle timeout fallback | Wall-clock inactivity timer in-app | Yes, always on as a backstop |

`platform-macos` owns the JNA bridge. The idle-timer fallback ships in
v1 regardless of whether the native notifications land in v1; if JNA
integration is painful, native signals can slip to a minor release —
but the timer must never be the *only* lock signal in a shipped build.

### 3.6 Master-key handling and log redaction

- v1: on each unlock, derive DB key from the typed password via FC-JDK's
  Argon2 pipeline — exactly what Android does on cold start today. No
  Keychain, no JNA for key storage.
- v2 (optional, not in scope): Keychain-wrapped master key via JNA +
  Apple Security framework.
- Sensitive `char[]` / `byte[]` wiped after use (already the pattern in
  FC-JDK).
- **Log redaction policy set in Phase 1**, not Phase 5: document the
  fields that must never hit logs (private keys, seeds, decrypted
  values, password-derived keys, raw signatures before broadcast).
  Implement as a single `RedactingPatternLayout` for logback so leakage
  is contained by construction, not by code review.

---

## 4. Phased Migration Plan

### Phase 0 — Preparation (3–5 days)

- **Stack spike (2 days, mandatory)** — §3.1.
- **Apple Developer account access.** $99/yr, required for signing and
  notarization. Without it, Phase 4 is blocked. Identity verification
  can take days for a new account — start this on day 1.
- **Universal vs Apple Silicon-only decision.** v1 can ship arm64-only
  unless there's a reason not to. Affects the `jpackage` pipeline.
- **Delete `.bak` files** from `Safe/app/` so they don't inflate port
  scope.
- **One-day FC-JDK ↔ Safe/FC-AJDK diff.** For each file present in both,
  classify as (a) identical, (b) cosmetic only, (c) FC-JDK is newer,
  (d) **Safe/FC-AJDK has a functional fix FC-JDK lacks**. Backport any
  (d) entries into FC-JDK so macOS consumes the fixed version. Tag the
  FC-JDK commit that macOS will pin against.
- `git init` the empty `/Users/liuchangyong/MacApp/SafeForMac/`; create
  the Gradle multi-module layout (§3.2). Use `includeBuild` to point at
  local FC-JDK during development.
- **Smoke test**:
  1. Build a trivial JVM program that does FC-JDK key-gen + sign +
     verify. Confirms BouncyCastle and freecashj behave on plain
     macOS JVM.
  2. Same program with the UI stack's dependencies added. Confirms
     FC-JDK's flat packages (`utils`, `core`, etc.) don't collide with
     Compose / JavaFX / logback transitives (§2.2).
  3. Capture golden vectors from FC-JDK on macOS: Argon2 KDF output,
     ECC sign/verify, AES-GCM encrypt/decrypt, multisig partial-sig
     serialization. These are the regression fixtures for Phase 5.

### Phase 1 — Platform glue + storage (4–6 days)

- Implement in `platform-macos`:
  - `SqliteDB : LocalDB` (value-layer encrypted KV)
  - `DesktopAppPaths` returning
    `~/Library/Application Support/com.fc.safe/…`
  - logback configuration with `RedactingPatternLayout` (§3.6)
  - Replace `slf4j-nop` (silent) with `logback-classic`
- Port `DatabaseManager` from `Safe/app/db/` — behavior unchanged
- Port `ConfigureManager` and `SafeApplication` lifecycle into a
  `DesktopApp` singleton
- Capture JSON export fixtures from the current Android build; verify
  import round-trips on macOS. This is the cross-platform interop gate.
- Smoke test: create/open contexts, write and read an `FcEntity`,
  reopen app, verify round-trip.

### Phase 2 — Core feature UIs (6–8 weeks)

For a solo developer porting 50+ screens plus multisig. Compresses only
if the navigation library and shared primitives land well.

**Before screen porting** (~1 week within Phase 2):
- Wire the chosen navigation library. **Hand-rolling is not the path
  for 50+ screens** — back-stack, transitions, lifecycle, and typed
  arguments add up past ~15 screens. Pick Voyager or Decompose
  (Compose) or `javafx-weaver` (JavaFX).
- Build shared primitives every screen depends on:
  - `AppShell` (top bar + content slot) — replaces
    `ToolbarUtils.setupToolbar(...)`
  - `SafeButton` — replaces `@style/ButtonStyle`
  - Snackbar / status component — replaces `Toast` / `ToastManager`;
    **keep the old API signature** so call sites don't need rewriting
  - Password prompt dialog — used by many screens
  - Result / confirmation dialogs
- Establish the JSON-in/JSON-out screen contract (already
  `fcEntity.toJson()`) as the navigation payload format
- macOS-native niceties: menu bar, Cmd-shortcuts (Cmd+Q, Cmd+W,
  Cmd+C/V, Cmd+,)

Port Activities to screens in this order (lowest-risk / highest-leverage
first):

1. **Home + password entry** — `MainActivity`, `HomeActivity`
2. **Key management** — `MyKeysActivity`, all `CreateKeyBy*`,
   `ImportKeyInfoActivity`, `ExportKeysActivity`, `BackupKeysActivity`
3. **Encrypt / Decrypt / Sign / Verify / Hash** — self-contained;
   validates the full crypto→UI wiring early
4. **Secrets + TOTP** — `SecretActivity`, `ImportSecretActivity`,
   `UpdateSecretActivity`, `TotpActivity`, `TotpCard`
5. **Transactions** — `CreateTxActivity`, `SignTxActivity`,
   `ImportTxInfoActivity`
6. **Multisig** — `ChooseMultisignIdActivity`,
   `CreateMultisignIdActivity`, `BuildMultisignTxActivity`,
   `SignMultisignTxActivity`, `MultisignTxDetailFragment`
7. **Cash / FID list / Article display** — remaining utility screens

For each screen: read JSON in/out contract, reuse `AppShell` +
`SafeButton`, replace intent extras with navigator arguments, replace
`Toast` with the snackbar shim.

### Phase 3 — QR, platform polish, lock detection (1–1.5 weeks)

- File-based QR import (drag-drop onto window, or "Open QR image…" menu)
- Clipboard QR image *and* text import (macOS clipboard can carry either)
- **Lock detection per §3.5**: wire focus-loss, screen-lock
  notification (JNA), sleep notification (JNA), plus the inactivity
  timer as always-on fallback
- macOS menu bar + standard keyboard shortcuts
- Optional: live camera QR scan via AVFoundation + JNA —
  **defer if risky; v1 ships without it**

### Phase 4 — Packaging, signing, notarization (3–5 days)

- `jpackage` with bundled JRE → `.app` and `.dmg`
- **Info.plist entitlements** for hardened runtime:
  `com.apple.security.cs.allow-jit` if the JVM needs it, camera /
  file-access entitlements only if those features shipped; sandbox
  stance picked deliberately
- Apple Developer ID Application signing; `notarytool` submission;
  stapling
- Auto-update strategy (Sparkle-compatible feed, or an in-app "check
  for updates" endpoint)
- If universal: build per-arch and `lipo`-merge

### Phase 5 — QA and parity check (1–2 weeks)

- Walk every feature listed in §1 against the Android version
- **Re-run Phase 0 golden vectors on the packaged build** — not just
  the dev build — since packaging can silently change classpath order
- **JSON interop test (end-to-end)**: export from Android → import on
  macOS, round-trip for keys (phrase / privkey / prikey-cipher /
  pubkey / FID), secrets, TOTP, single-sig tx, multisig partial sig
- Security review:
  - No secret material on disk unencrypted
  - Sensitive `char[]` / `byte[]` wiped after use (regression check;
    FC-JDK already enforces this)
  - Log files inspected for redaction correctness
    (`RedactingPatternLayout` catches what's declared; unknown leak
    paths caught here)
  - Lock detection actually fires on screen-lock and sleep (not just
    focus-loss); if not, v1 does not ship

---

## 5. Risks and Open Questions

- **FC-JDK drift against Safe/FC-AJDK.** The Phase 0 one-day diff is
  the only safety net for fixes made on the Android side that never
  reached FC-JDK. If the diff turns up heavy divergence, that's a
  signal the deferred consolidation project needs to happen sooner —
  not a reason to proceed anyway with a known-stale library.
- **FC-JDK's flat packages** (`package utils;`, etc.) can collide with
  other jars on the classpath. Phase 0 smoke test catches this with the
  UI-stack deps present. If a collision surfaces late, the options are:
  (a) rename inside FC-JDK (becomes the consolidation project), or (b)
  shade FC-JDK into a macOS-specific artifact with a prefixed package.
- **FC-JDK includes server-side code** (`server`, `fapi`, `fudp`,
  `managers`, Netty, servlet-api, Apache HttpComponents). The wallet
  doesn't use any of it, but if FC-JDK publishes as one artifact these
  deps land on the macOS classpath. Mitigations in order of preference:
  (a) ask FC-JDK's pom for a `wallet`-classifier or split `fc-core` vs
  `fc-server` modules, (b) accept the ~10–15 MB of extra jars, (c) use
  Gradle dependency excludes for the transitive HTTP stack. For v1,
  (b) is fine; flag (a) as follow-up.
- **Stack decision**: JavaFX vs Compose Desktop is a spike outcome, not
  an opinion. `jpackage` pain doesn't show until notarization — the
  spike is specifically there to catch that before Phase 2 commits.
- **Camera QR on macOS**: AVFoundation via JNA is non-trivial.
  Acceptable to ship v1 with file + clipboard import only.
- **Lock detection beyond focus-loss**: JNA integration for screen-lock
  and sleep observers is the riskiest native-integration work in this
  port. Timer-only fallback is not sufficient for a shipping wallet —
  plan the JNA work into Phase 3 explicitly.
- **Master-key storage**: v1 derives from typed password on each
  unlock. Keychain via JNA is a v2 option, not a v1 dependency.
- **Notarization prerequisites**: Apple Developer account ($99/yr) and
  a signing certificate must be in place before Phase 4.
- **Multisig interop**: binary formats must match Android byte-for-byte.
  Golden vectors + JSON interop tests are mandatory, not optional.
- **Storage search trade-off** (§3.4): opaque ciphertext values
  preclude SQL-side filtering. Revisit deliberately when a feature
  needs it.
- **Cross-platform later**: If iOS is wanted later, the Compose
  Multiplatform stack choice keeps the door open; otherwise a future
  iOS port is a separate project.

---

## 6. Summary Estimate

| Phase | Effort |
|---|---|
| 0 — Prep + stack spike + FC-JDK diff + Apple Dev setup | 3–5 days |
| 1 — Platform glue (SQLite, paths, logging) + storage | 4–6 days |
| 2 — UI port (navigation + primitives + all features) | 6–8 weeks |
| 3 — QR + platform polish + lock detection | 1–1.5 weeks |
| 4 — Packaging, signing, notarize, entitlements | 3–5 days |
| 5 — QA + interop parity | 1–2 weeks |

**Total: roughly 9–12 weeks of focused work for a single developer.**
No upstream dependency on the FC-JDK consolidation project. Phase 0's
diff + golden-vector run is the real de-risking moment: if the diff is
clean and vectors match on macOS, the remaining work is UI at known
cost. If Compose Multiplatform is chosen, Kotlin/Compose ramp-up is
absorbed inside Phase 2.
