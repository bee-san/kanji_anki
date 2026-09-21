# Anki Desktop CI fixture

`ci/scripts/run_anki_desktop_fixture.sh` boots a pinned Anki Desktop with a
pinned AnkiConnect against a **throwaway profile**, so Kani's desktop provider
can be qualified against a real Anki instead of only a mock HTTP server.

Nothing here touches the operator's own Anki. Three properties keep the two
apart, and each is asserted by `ci/tests/test_run_anki_desktop_fixture.py`:

- **A separate base directory.** Anki keeps profiles, collections, and add-ons
  under `-b <base>`; the fixture always passes its own, never the default
  `~/.local/share/Anki2`.
- **A separate AnkiConnect port.** The fixture's add-on copy binds `18765`, not
  the standard `8765`. An operator's live Anki can stay running throughout, and
  a fixture request cannot arrive at it by accident.
- **A separate add-on copy.** The pinned AnkiConnect is unpacked into the
  fixture base, so the operator's installed add-ons and their configuration are
  neither read nor rewritten.

`anki-desktop-26.05.sha256` pins the two downloads. Update it only from digests
published with the corresponding upstream release and commit.

## Prerequisites

Anki Desktop is a Qt application, so a headless host needs an X server and the
xcb platform plugin's libraries. On Amazon Linux 2023:

```sh
sudo dnf install -y xorg-x11-server-Xvfb xcb-util-cursor libxkbcommon-x11 \
  xcb-util-wm xcb-util-keysyms
```

Missing libraries surface as `no Qt platform plugin could be initialized`. To
find which one, ask the plugin directly:

```sh
ldd anki-linux/app_packages/PyQt6/Qt6/plugins/platforms/libqxcb.so | grep "not found"
```

## First run is non-interactive

A fresh Anki base directory has no `prefs21.db`, and Anki then opens a modal
**language** dialog and waits — forever, on a headless host, with AnkiConnect
never starting because the main window never loads. Driving that dialog would
need `xdotool`, which is not packaged for AL2023.

`ci/scripts/seed_anki_desktop_profile.py` avoids the dialog instead of fighting
it: it writes `prefs21.db` before launch with `firstRun` already false and
`defaultLang` set, using Anki's own `aqt.profiles` defaults read out of the
bundled interpreter. The seed is therefore not a hand-copied schema that drifts
when Anki's defaults change.

## Profile identity

`getMediaDirPath` reports the **loaded** profile's media directory and is how
Kani identifies which collection it is bound to. This fixture is what
established that: `getProfiles` lists every profile on the machine regardless of
which is open, so it cannot answer the question, and `getActiveProfile` — which
Kani originally probed — is not an AnkiConnect action at all. Launching the
fixture with `-p KaniSecond` moves `getMediaDirPath`'s answer with it, which is
the check that distinguishes "tracks the active profile" from "happens to name
the first one".
