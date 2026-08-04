# Desktop Native Packaging

Kani's desktop packages are built by Compose's `jpackage` integration: an MSI on Windows,
a DEB on Linux, a DMG on macOS. Each format is built only on its own OS — Compose
packaging is not cross-compilation — so most of this configuration is verified on a CI
runner rather than on any one developer's machine.

This document records the packaging configuration, how it was verified, and the licensing
audit Goal 204 requires, including the parts that are not done.

## Where the configuration lives

`kani.desktop-application-conventions.gradle.kts` configures `nativeDistributions`, and
every value it uses comes from `KaniDesktopIdentity` / `KaniDesktopPackageVersions` in
`build-logic`. The packaging JDK is pinned separately; see
`docs/desktop-packaging-jdk.md`.

`KaniDesktopIdentityTest` asserts against the convention script's *text* and against the
entitlements files' *content*. That is unusual and deliberate: these settings have no
observable effect on this host, because this host cannot build any of the three formats.
Reading the file is the only check available locally, so it is the check that runs.

## How the settings were verified

Property names were taken from the Compose 1.11.1 plugin's own bytecode
(`WindowsPlatformSettings`, `LinuxPlatformSettings`, `AbstractMacOSPlatformSettings`,
`JvmMacOSPlatformSettings`) rather than from documentation, and the jpackage flags they
map to were read out of `AbstractJPackageTask`.

The emitted command line was then confirmed directly. `--info` does not log jpackage's
arguments, because the plugin invokes jpackage with an `@argfile`; that file is written
before jpackage runs and survives the failure. From
`desktop-app/build/compose/tmp/packageDeb.args.txt` on this host:

```
--linux-shortcut
--linux-package-name "kani"
--linux-app-category "Education"
--linux-deb-maintainer "bee-san <bee@bee.gg>"
--linux-menu-group "Kani"
--copyright "Copyright (c) bee-san"
--app-version "0.4.33-1"
--icon ".../packaging/icons/kani.png"
```

jpackage then failed with `Error: Invalid or unsupported type: [deb]`, which is this
host's missing `dpkg-deb`/`fakeroot` and not a configuration fault. The argfile is
therefore the evidence that the DSL reaches the tool; the DEB's *contents* are verified
on a runner that has the tooling.

## Windows

| Setting | Value | Why |
| --- | --- | --- |
| Format | MSI x64 | |
| Upgrade UUID | `C972670E-…` | Stable across releases, or Windows treats each release as an unrelated product |
| Version | `KaniDesktopPackageVersions.windowsMsi` | Fails closed above MSI's numeric bounds rather than truncating |
| Install scope | Per-user | |
| Start menu | Yes, group `Kani` | |
| Desktop shortcut | Yes | |
| Directory chooser | Yes | |
| Console | No | |

**Per-user install is the substantive choice.** Kani already keeps all user data per-user
(`%LOCALAPPDATA%\Kani` for data and cache, `%APPDATA%\Kani` for config — see
`DesktopStorageLayout`), so a per-machine install would demand elevation on every update
and gain nothing in return. `perUserInstall` defaults to `false`, so a test pins it: losing
the line is a silent switch back to an elevated install, and it is invisible to anyone
developing on a machine where they are an administrator.

## Linux

| Setting | Value | Why |
| --- | --- | --- |
| Format | DEB x64 | |
| Package name | `kani` | `dpkg` rejects an uppercase name |
| Version | `0.4.33-1` | Semantic version plus an explicit Debian revision |
| Desktop entry | Yes, category `Education`, group `Kani` | |
| Maintainer | `bee-san <bee@bee.gg>` | Mandatory in a control file |

The lowercase `packageName` is load-bearing. Without it jpackage derives `Kani` from the
application name and packaging fails — on a host that *has* DEB tooling, which is not the
host this work was developed on. That makes it exactly the kind of defect that first
appears in CI or in a release.

## macOS

| Setting | Value | Why |
| --- | --- | --- |
| Format | DMG (arm64) | |
| Bundle ID | `dev.bee.kanjianki.desktop` | Stable; macOS identity for preferences and TCC grants |
| Short version | `KaniDesktopPackageVersions.macOsJpackage` | jpackage rejects a leading zero component |
| Build version | Kani's monotonic version code | Orders two builds sharing a short version |
| Minimum system | 13.0 | See the caveat below |
| Category | `public.app-category.education` | |
| Entitlements | `packaging/macos/kani.entitlements` (+ runtime) | |

### Entitlements

Kani is not signed yet. The entitlements files exist so that enabling signing later is a
credential change rather than a behavioral one — a signed, notarized app missing
`allow-jit` crashes at launch on a user's machine while every unsigned local build works,
and nothing before the first signed release would say so.

The application file grants `allow-jit`, `allow-unsigned-executable-memory`,
`disable-library-validation`, and `allow-dyld-environment-variables`. The runtime file
(jpackage signs the embedded JDK separately) grants the first three only: the launcher, not
the runtime, sets the JVM's DYLD variables. Keeping the two distinct rather than identical
is the point — granting the runtime everything would widen the signed surface for no gain,
invisibly.

Neither file claims a sandbox entitlement. Kani is unsandboxed and not distributed through
the App Store, and a sandbox entitlement without `com.apple.security.app-sandbox` has no
effect at all — so listing `network.client` would read as though Kani's loopback
AnkiConnect access had been granted by that file, when in fact it works because the process
is unsandboxed. A test asserts their absence for that reason.

## What the artifact-side gate checks

`./gradlew verifyDesktopPackage` runs `tools/verify_desktop_package.py` against the built
image. It verifies the launcher, the shipped `JAVA_VERSION`, the exact runtime `MODULES`
set, and that no class is shadowed across the bundled jars. It reports the runtime *vendor*
as explicitly unverifiable, because the packaged `jlink` image records no `IMPLEMENTOR` and
nothing under `runtime/legal/` or `runtime/conf/` names a distribution either (checked, not
assumed). Vendor provenance is established at build time instead.

### Bundled-class shadowing

The image bundles 81 jars in one flat directory, four of them at two versions each
(`runtime-desktop` 1.11.1 and 1.11.2, `runtime-saveable-desktop`,
`lifecycle-runtime-compose-desktop`, `savedstate-compose-desktop`). This is harmless, and
the reason is measured rather than assumed: Compose 1.11 moved these artifacts from
`org.jetbrains.compose` to `androidx.compose` and publishes the old coordinates as empty
redirect stubs, so each pair shares zero classes. Across the whole image the only repeated
entry is `META-INF/versions/9/module-info.class`, which a flat-classpath launch never
loads.

A duplicated version and a genuine shadowing conflict are indistinguishable in a file
listing *and* in the dependency graph, so the gate counts classes.

## Licensing and attribution audit

Goal 204 requires auditing every bundled JVM/native/resource/font/dictionary dependency and
shipping the required licenses and attributions. This is the audit. **It is not yet
satisfied** — the gaps are listed at the end.

### Bundled JVM dependencies

All 81 bundled jars are Apache-2.0, confirmed from each artifact's POM `<licenses>` block
(Compose/`androidx` Compose, Skiko, Kotlin and kotlinx, `androidx.lifecycle`,
`androidx.savedstate`, `androidx.sqlite`, `androidx.navigation*`, `androidx.collection`,
`androidx.annotation`, `graphics-shapes`, `jbr-api`, JSpecify).

Note that **none of the 81 jars carries a `META-INF/LICENSE` or `META-INF/NOTICE` file**
(checked: 0 of 81). Apache-2.0 §4(d) only requires propagating a `NOTICE` where one is
supplied, and none is — but it also means an aggregated notice file cannot be assembled by
harvesting the jars, and must be generated from POM metadata instead.

### Bundled native code

`libskiko-linux-x64.so` (29 MB) is Skiko, Apache-2.0. It is statically linked, and it
carries considerably more than Skiko's own code. Identified from symbols and embedded
copyright strings in the shipped binary:

| Component | Licence |
| --- | --- |
| Skia | BSD-3-Clause |
| ICU / Unicode data | Unicode licence |
| FreeType | FTL or GPLv2 (dual) |
| HarfBuzz | MIT (Old) |
| libpng 1.6.51 | PNG Reference Library licence |
| libjpeg-turbo | IJG / BSD-3-Clause / zlib |
| zlib | zlib licence |
| Expat | MIT |
| Adobe DNG SDK, piex, Wuffs | Adobe DNG SDK / BSD-3-Clause / Apache-2.0 |

**None of these notices is currently shipped**, and several of these licences require the
notice to accompany the binary. FreeType's dual licence in particular has to be resolved
to a stated choice rather than left implicit. This is the largest single item in the audit,
and it cannot be discharged from POM metadata — the obligations come from code compiled
into the `.so`, which no dependency-graph tool reports.

The equivalent Windows and macOS Skiko binaries are not enumerated here; they should be
assumed to carry the same set until checked on their own runners.

### Bundled JVM runtime

The `jlink` image ships its own `runtime/legal/` tree — 58 files across the 11 packaged
modules, covering the JDK's GPLv2-with-Classpath-Exception licence plus its third-party
components (ICU, CLDR, Unicode, zlib, ASM, and others). This is already inside every
package, so the JDK's obligations are met by the artifact itself.

### Dictionary and reference data

`app/src/main/assets/dictionaries/dictionary_sources.json` records each source with its
licence, upstream URL, fetch date, and SHA-256:

| Source | Licence |
| --- | --- |
| KANJIDIC2 | CC BY-SA 4.0 via EDRDG licence |
| KanjiVG | CC BY-SA 3.0 |
| Jiten kanji frequency ranks | Not recorded in the manifest |

SKIP query codes are deliberately excluded because EDRDG documents separate licensing
conditions for them, and no word-level dictionary data is bundled.

### AnkiConnect

AnkiConnect remains a separately installed user prerequisite. Goal 204 permits bundling
only after an explicit license/security review, which has not happened, so nothing in
Kani's packages contains or installs it.

### Open gaps

These are real and unresolved; none should be read as done.

1. **Kani has no licence.** There is no `LICENSE` or `COPYING` file at the repository
   root and the README declares none, so Kani's own terms are unstated. `COPYRIGHT`
   asserts authorship in each installer, which is not a licence grant. Choosing a licence
   is the user's decision, not one to make on their behalf, so this is surfaced rather
   than filled in.
2. **The desktop packages ship no attribution surface.** `AttributionCopy` exists in
   `:core` and is rendered only by `:app` (`AttributionTexts`); the shared
   `:feature-settings` has no licences route, so the desktop build displays none of the
   above to a user. The desktop image's `resources` directory is empty and the dictionary
   assets are Android-only, which is consistent — but the CC BY-SA attribution obligation
   attaches to whatever ships that data, so this must be resolved before the desktop
   package distributes dictionary content.
3. **None of `libskiko`'s nine-plus vendored native notices is shipped** (see the table
   above), and FreeType's dual licence needs a stated choice. This is the largest item.
4. **No aggregated third-party notice file is generated.** Since the jars carry no
   `NOTICE` files, this has to be produced from POM metadata as a build step, and the
   native notices have to be added by hand because no dependency-graph tool sees them.
5. **The Jiten rank data has no recorded licence** in the manifest.

## Host and qualification gaps

Goal 204 requires qualifying the *advertised minimums*, not just current hosts, and raising
the support floor wherever a real gate is unavailable. Current state:

| Claim | Qualified? |
| --- | --- |
| Windows 10/11 on real VMs | No |
| Ubuntu 20.04 (oldest glibc baseline) + current Ubuntu, clean container | No |
| macOS 13 on Apple silicon | No |

The `minimumSystemVersion = 13.0` pin is therefore a *declared* deployment target, not a
tested one. `LSMinimumSystemVersion` at least makes the claim enforceable at launch — an
older system refuses to open the bundle instead of failing somewhere inside Skiko, where the
cause is unrecoverable from a user's bug report.

Also not yet done: install/upgrade/uninstall/data-retention tests against a synthetic
lower-version package (Windows, sharing the upgrade UUID; macOS, sharing the bundle
identity), the macOS quarantine test, and documenting the WiX 3+/SignTool and Xcode
command-line prerequisites per runner.

## Building locally

```sh
./gradlew createDesktopDistributable   # app image, current host
./gradlew packageDesktopCurrentOs      # native installer, current host only
./gradlew verifyDesktopPackage         # artifact-side identity/runtime/classpath gate
./gradlew ciDesktopPackage             # all of the above plus smoke and budgets
```

On a headless host, prefix with `xvfb-run -a`. This development host cannot produce any
native package: no `dpkg-deb`/`fakeroot` for the DEB, and the MSI and DMG cannot be built
on Linux at all.
