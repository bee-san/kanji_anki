# Desktop Packaging JDK

The JVM inside Kani's desktop packages is part of the release artifact, not part of the
build environment. `jpackage` copies a stripped `jlink` image into `Kani/lib/runtime`, and
that is the JVM the user runs: every class Kani loads, every TLS handshake with
AnkiConnect, every glyph rasterized. This document records which JDK that is, how it is
enforced, and what the enforcement cannot see.

## The defect this pin closes

Before this pin, the JVM shipped to users was whichever JVM happened to be running the
Gradle daemon.

Compose's packaging `javaHome` defaults to `System.getProperty("java.home")`. Neither the
catalog's `jvmTarget = "17"` nor `java { toolchain { languageVersion … } }` constrains it,
because neither is consulted on that path — `jvmTarget` is a compiler setting and the Java
toolchain configures compilation and test execution, not `jlink`.

This was reproduced rather than inferred. Building this repository with
`-Dorg.gradle.java.home=<Temurin 21>` and no other change produced:

```
$ cat desktop-app/build/compose/binaries/main/app/Kani/lib/runtime/release
JAVA_VERSION="21.0.11"
MODULES="java.base java.datatransfer java.xml java.prefs java.desktop java.instrument java.logging java.net.http jdk.accessibility jdk.crypto.ec jdk.unsupported"
```

`BUILD SUCCESSFUL`. No warning, nothing in the log, and the only evidence anywhere was
that one line inside the installed image. A release cut from a machine whose daemon JVM had
drifted — a CI runner image bump, a developer's `mise` default, a `JAVA_HOME` export —
would have shipped users a different JVM than the one that was tested, and the tests would
all still have passed, because tests run on the daemon's JVM too.

This is the third defect in this class found in the desktop packaging work, after the
missing `java.net.http` and the missing `jdk.accessibility`
(`docs/desktop-performance-budgets.md`, `docs/desktop-accessibility-and-keybindings.md`).
All three share a shape: the packaged artifact differs from what every green check
described, and only inspecting the artifact shows it.

## What is pinned

`KaniPackagingJdk` in `build-logic` is the single source.

| Property | Value |
| --- | --- |
| Distribution | Eclipse Temurin (`IMPLEMENTOR="Eclipse Adoptium"`) |
| Version | `17.0.19+10` (`IMPLEMENTOR_VERSION="Temurin-17.0.19+10"`) |
| Feature version | 17 |

Eclipse Temurin is the preferred vendor named in Goal 204, and the vendor is load-bearing
rather than a preference: Compose's own packaging check refuses Homebrew's JDK outright
(JetBrains issue 3107), and a distribution's `jlink`/`jpackage` behavior and bundled
certificate set are part of what ships to users.

Per-host archives, with the exact bytes:

| OS | Arch | Archive | SHA-256 |
| --- | --- | --- | --- |
| linux | x64 | `OpenJDK17U-jdk_x64_linux_hotspot_17.0.19_10.tar.gz` | `d8afc263758141a66e0e3aafc321e783f7016696f4eaea067d340a269037d331` |
| linux | aarch64 | `OpenJDK17U-jdk_aarch64_linux_hotspot_17.0.19_10.tar.gz` | `83a52172678ec8975164648654869cb2e71d7c748b47aca94b29bbfa10c18e81` |
| windows | x64 | `OpenJDK17U-jdk_x64_windows_hotspot_17.0.19_10.zip` | `b5b235c48adf6a081874b812c630b9f4b5f637b7a5ed18b9174d08a41ec4c235` |
| mac | x64 | `OpenJDK17U-jdk_x64_mac_hotspot_17.0.19_10.tar.gz` | `03632d1fbf139ab3719a9f4b47dc206251449b87557143c822336dbf8c06560f` |
| mac | aarch64 | `OpenJDK17U-jdk_aarch64_mac_hotspot_17.0.19_10.tar.gz` | `8fa1eff40bb637a33613b2ccb8b12c70dc3661cc22cf8e784943715769a05336` |

All five are from `api.adoptium.net` for `17.0.19+10`, `image_type=jdk`,
`vendor=eclipse`, and each URL is an Adoptium release asset on
`github.com/adoptium/temurin17-binaries`.

**Windows ARM64 is absent because Temurin 17 has no Windows ARM64 build** — the Adoptium
API returns nothing for that combination. The consequence is real and worth stating: Kani's
Windows package is x64 and runs under emulation on ARM64 Windows.
`KaniPackagingJdkTest.theWindowsArm64GapIsRecordedRatherThanImplied` holds the gap open, so
it cannot be quietly filled with a guessed URL — a wrong checksum would surface at install
time on a platform nobody tested, reading as a corrupted download rather than as an
unsupported host.

## How it is enforced

Two layers, because one cannot do the job alone.

**Gradle's toolchain service** resolves vendor and feature version:
`languageVersion = 17`, `vendor = JvmVendorSpec.ADOPTIUM`.

**`KaniPackagingJdk.verify` checks the exact build**, by reading the resolved
installation's own `release` file and comparing `IMPLEMENTOR`, `IMPLEMENTOR_VERSION`, and
`JAVA_VERSION`. This second layer exists because a toolchain spec cannot express a patch
version — `languageVersion` is the feature version only, so a query for "Adoptium 17" is
satisfied by any 17.x. Without it, a CI image moving from 17.0.19 to 17.0.20 would ship a
different runtime than the tested one and nothing would say so.

Verification is fatal, not a warning. The whole value of the pin is that a release cannot
be built with an unpinned runtime, and during a release a warning in a Gradle log is
indistinguishable from silence. The failure names what was found, what was expected, and
this host's pinned URL and checksum, because the useful response is to install that archive
rather than to read the source.

### Where it is wired, and the ordering trap

The Compose plugin points every packaging tool at `application.javaHome`. The convention
overrides that on four task types:

- `AbstractJvmToolOperationTask` — `jlink` and `jpackage` both extend it.
- `AbstractSuggestModulesTask` — the module scan.
- `AbstractCheckNativeDistributionRuntime` — the runtime probe, which matters as much as
  the rest because its result is what the plugin believes the available module set to be.

The override runs inside `afterEvaluate`, and that is required rather than stylistic. The
plugin registers these tasks and assigns their `javaHome` from *its own* `afterEvaluate`,
so a `configureEach` added while the convention script is still evaluating is applied
first and then silently overwritten. The first version of this change did exactly that:
`testBuildLogic` passed, and a `--rerun-tasks` build on a Temurin 21 daemon still shipped
`JAVA_VERSION="21.0.11"`. Only re-reading the installed image caught it.

The resolution is also lazy — the DSL's `application.javaHome` is a plain `String`, so
assigning it would resolve the toolchain at configuration time and make
`:desktop-app:test` and `:desktop-app:check` require the packaging JDK to be installed.
Only a packaging run should need it.

### Auto-provisioning is deliberately off

No toolchain resolver is configured in `settings.gradle.kts`, so Gradle cannot download a
JDK to satisfy the spec. The URLs and checksums above are evidence and an install source,
not something this build fetches: routing the JVM that ships inside the application through
a resolver service at build time would make the shipped runtime depend on that service's
answer on the day. CI installs the pinned archive explicitly (`actions/setup-java` at the
exact `17.0.19+10`) and the verification then confirms that what got installed is what was
pinned.

## What this cannot verify

**The packaged runtime carries no vendor identity.** The `jlink` image's `release` file has
`JAVA_VERSION` and `MODULES` and nothing else — no `IMPLEMENTOR`, and nothing under `conf/`
or `legal/` names a distribution either:

```
$ cat Kani/lib/runtime/release
JAVA_VERSION="17.0.19"
MODULES="java.base java.datatransfer java.xml java.prefs java.desktop java.instrument java.logging java.net.http jdk.accessibility jdk.crypto.ec jdk.unsupported"
```

So provenance cannot be recovered from the artifact after the fact. It can only be
established by checking the building JDK at the moment it builds, which is what `verify`
does. A `verifyDesktopPackage` gate can assert the image's `JAVA_VERSION` and `MODULES`
against the pin, and it must not claim to have verified the vendor from the image, because
the image does not say.

## Verified behavior

On the Linux x64 measurement host, with both Temurin 17.0.19+10 and Temurin 21.0.11+10
installed:

| Build | Shipped `JAVA_VERSION` |
| --- | --- |
| Default daemon (Temurin 17.0.19+10) | `17.0.19` |
| `-Dorg.gradle.java.home=<Temurin 21>`, before the pin | `21.0.11` |
| `-Dorg.gradle.java.home=<Temurin 21>`, `configureEach` outside `afterEvaluate` | `21.0.11` |
| `-Dorg.gradle.java.home=<Temurin 21>`, pin as committed | `17.0.19` |

The last row is the contract: the JVM that ships is the pinned one regardless of which JVM
runs the build.

## Changing the pin

1. Fetch the new version's real per-host URLs and checksums from
   `https://api.adoptium.net/v3/assets/version/<version>?architecture=<arch>&image_type=jdk&os=<os>&vendor=eclipse`.
   Do not hand-edit a version string in an existing URL — the archive name and the digest
   both change, and a stale digest is a failure at install time.
2. Update every field in `KaniPackagingJdk` and the tables above together.
3. Update the pinned `java-version` in `.github/workflows/desktop-ci.yml`.
4. Rebuild the installed image and read `Kani/lib/runtime/release`. A green build is not
   evidence here; that is the whole lesson of this document.
