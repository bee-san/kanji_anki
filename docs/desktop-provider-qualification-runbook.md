# Desktop provider qualification against real Anki Desktop

Kani's desktop provider talks to Anki through AnkiConnect's HTTP API on
loopback. A fake transport can pin the shape Kani *expects*, but it cannot find
the places where a real Anki answers differently — and both defects found so far
were exactly that: reachable only from a real host, invisible to every mock.

This runbook is the repeatable procedure. It covers the isolated fixture, the
sanitized collection, the qualification suite, the evidence recorded, and the
stricter local real-collection gate.

## The rule that shapes everything below

**The operator's own Anki profile is never written to.** Every write in this
procedure — `addTags`, `createDeck`, `createModel`, `addNotes` — goes to a
throwaway profile on an isolated port. Read-only checks against a running
personal session are allowed; writes are not, ever, and there is no flag to
override that.

Two independent guards enforce it, and both *fail the run* rather than skipping:

1. **The port must not be 8765.** That is AnkiConnect's standard port, where a
   real Anki listens. The fixture uses `18765`.
2. **The loaded profile must be the expected throwaway profile.** This is
   answered by `getMediaDirPath`, which returns the *loaded* profile's media
   directory. `getProfiles` cannot answer it — it lists every profile on the
   machine regardless of which is open — and `getActiveProfile` is not an
   AnkiConnect action at all.

Both guards live in two places, because both a Python seeder and a Kotlin suite
write: `ci/scripts/seed_anki_desktop_kiku_collection.py` and
`LiveAnkiDesktopQualificationTest`. `ci/tests/` pins both.

## Pinned versions

| Component | Pin |
| --- | --- |
| Anki Desktop | `26.05` (`anki-26.05-linux-x86_64.tar.zst`) |
| AnkiConnect | commit `4064fa142785975255457abd6a496015f5b71f38` |
| AnkiConnect API version | `6` |
| Port | `18765` |
| Profile | `KaniFixture` |

Both downloads are SHA-256 verified against
`ci/fixtures/anki-desktop/anki-desktop-26.05.sha256` before use;
`ci/tests/test_run_anki_desktop_fixture.py` pins the digests, so a substituted
tarball fails the deterministic gate rather than the live one.

## Step 1 — start the isolated fixture

```sh
ci/scripts/run_anki_desktop_fixture.sh
```

It downloads and verifies both pins, seeds a `KaniFixture` profile with the
first-run language dialog suppressed (a headless boot otherwise hangs on a modal
with no error), installs AnkiConnect into the fixture's *own* add-on directory,
and launches Anki under `xvfb-run` against the fixture's own base directory — so
it can neither read nor rewrite the operator's profiles and add-ons.

```sh
# Confirm the loaded profile before going further.
ci/scripts/run_anki_desktop_fixture.sh --probe getMediaDirPath
# .../base/KaniFixture/collection.media

ci/scripts/run_anki_desktop_fixture.sh --stop
```

Overrides, with defaults: `KANI_ANKI_DESKTOP_WORK_DIR`
(`${TMPDIR:-/tmp}/kani-anki-desktop-fixture`), `KANI_ANKI_DESKTOP_PORT`
(`18765`), `KANI_ANKI_DESKTOP_PROFILE` (`KaniFixture`),
`KANI_ANKI_DESKTOP_VERSION` (`26.05`), `KANI_ANKICONNECT_COMMIT`. Setting the
port to `8765` is refused with exit `2`.

> A note for anyone editing the launcher: its stop step runs from a generated,
> `setsid`-detached script on purpose. `pgrep -f Xvfb` matches the *calling*
> shell's own command line, so an inline kill loop kills the caller. That cost
> three dead shells before the generated-script form.

## Step 2 — seed the sanitized collection

```sh
ci/scripts/seed_anki_desktop_kiku_collection.py
ci/scripts/seed_anki_desktop_kiku_collection.py --print-manifest
```

Eight notes in a `Kiku` model, every kanji, reading, gloss, and sentence
invented. That is what makes the evidence publishable: no user content ever
reaches this collection.

| Card state | Notes | Why the suite needs it |
| --- | --- | --- |
| Mature review | 4 | Evidence seeding and the maturity threshold |
| Suspended (`queue = -1`) | 1 | Archive tagging, suspended-only evidence |
| Buried (`queue = -2`) | 1 | Kani suspends on `queue < 0`, wider than AnkiConnect's own `areSuspended` |
| Learning | 1 | A real scheduler-produced learning state |
| New | 1 | No review history at all |
| Browser-query tagged | 1 | The merge path where the user's own search selects a note |

The seeder writes through AnkiConnect rather than authoring a `.anki2` the way
the AnkiDroid CI fixture does. Anki 26.05's schema is much newer, is migrated by
Anki's own Rust backend, and a file pinned to one schema version becomes a
maintenance trap the first time upstream migrates.

It is idempotent, and it *converges* rather than skipping: a note that already
exists has its scheduling write re-applied, because an interrupted earlier run
can leave the note added and the state unwritten, and a fixture that silently
keeps Anki's defaults is worse than one that fails.

### Two things only the real host revealed

- **Scheduling values must be JSON numbers.** Strings come back as
  `'str' object cannot be interpreted as an integer`.
- **`setSpecificValueOfCard` reports per-item failures inside `result`, with the
  envelope's `error` still `null`.** So a generic envelope-level success check
  passes while nothing was written. The first version of the seeder sent strings
  and reported eight seeded notes with every card left at Anki's defaults.

**A limit worth recording:** AnkiConnect refuses a negative `ivl` outright
(`Value out of range: -600`), so Anki's legacy negative-seconds interval
encoding cannot be produced through this fixture. `ProviderCardPolicy`'s
interval floor is therefore **not** covered here — it defends the AnkiDroid path
and older collections, and is pinned by
`AnkiDroidCrossProviderConformanceInstrumentedTest` over a synthetic cursor. Do
not simplify it away on this fixture's strength.

## Step 3 — run the qualification suite

```sh
./gradlew :provider-ankiconnect:test \
  --tests 'dev.bee.kanjianki.provider.ankiconnect.LiveAnkiDesktopQualificationTest' \
  -Pkani.liveAnkiDesktop=true
```

Optional: `-Pkani.liveAnkiDesktopEndpoint=...`,
`-Pkani.liveAnkiDesktopProfile=...`.

Without `-Pkani.liveAnkiDesktop=true` the suite skips itself, so the
deterministic gate never needs a live Anki. The switches are forwarded as Gradle
*properties* rather than read from the daemon's system properties, which keeps
the opt-in explicit per invocation: a daemon reused from an earlier live run
cannot silently re-enable a suite that writes to a collection.

Seventeen tests, covering:

- Handshake, version, and the capability set this Anki actually reports.
- Note types and their field lists.
- The configured read: every note, real scheduling state, no skipped or
  malformed rows, template ordinal 0 only, `queue < 0` counted as suspended.
- Progress stages and cancellation.
- The provider collection snapshot's source identity.
- Repeated reads of an unchanged collection being identical.
- The cross-provider agreed field set being fully populated.
- Browser-query merge — the query *marks* notes, it does not filter them.
- Inventory scan over the whole collection.
- Archive tag write, including idempotence on re-run.
- Repaired tag write, including the empty no-op.
- The Missing Kanji export, then its already-present retry with no duplicate.
- The browser handoff being accepted.
- `source_key` binding to the loaded profile, not just the endpoint.

Three of those write. They are scoped to the fixture — the Missing Kanji deck is
`KaniFixture::Missing Kanji`, inside the fixture's own namespace, rather than the
production `Kani::Missing Kanji` default.

## Evidence — 2026-07-31

Aggregate and sanitized only: counts, capability and action lists, and outcomes.
No field, deck, or model content from any personal collection appears here.

```text
Anki Desktop           26.05
AnkiConnect            4064fa142785975255457abd6a496015f5b71f38
AnkiConnect API        version 6
Endpoint               http://127.0.0.1:18765
Profile                KaniFixture (throwaway)

Required actions        12/12 present, 0 missing
Optional actions        10/10 present, 0 missing
Gateway capabilities    READ_COLLECTION, LIST_NOTE_TYPES, NOTE_TAG_WRITE
Writer capabilities     MISSING_KANJI_WRITE
Never advertised        FSRS_MEMORY_STATE

Collection read         8 notes, 8 cards, 0 skipped, no malformed-row warning
Suspended (queue < 0)   2   (1 suspended + 1 buried)
Mature active           3
Negative intervals      0
Archive tag write       2 notes tagged, 0 deleted, idempotent on re-run
Missing Kanji export    1 note created, then 1 already-present on retry,
                        0 duplicates, stable destination_key
Repeated reads          identical

Result                  17 tests, 0 failures, 0 skipped (2 consecutive runs)
Wall clock              3.2 s
```

**AnkiConnect exposes no FSRS memory state.** `FSRS_MEMORY_STATE` is never
advertised on this provider and stability, difficulty, and retrievability stay
null. Interval and lapse evidence is present, so `AdmissionEvidencePolicy` still
has a maturity signal to seed from; it just derives it from the interval rather
than from FSRS state, exactly as it does for an AnkiDroid collection without
FSRS.

### Defects this gate found that mocks could not

1. **The profile-identity probe used an action AnkiConnect does not have.**
   `getActiveProfile` is not an AnkiConnect action, so the handshake reported
   *every* real Anki as unavailable while passing against a mock that implemented
   the name. `getMediaDirPath` is the correct probe, and it is the only one that
   reports the loaded profile.
2. **The envelope codec rejected fractional numbers.** Real Anki's
   `getDeckConfig` carries `"delays": [1.0, 10.0]` and `"ease4": 1.3`. The codec
   threw on any non-integer, so the whole response decoded to `null`, surfaced as
   a protocol error, and `AnkiConnectMissingKanjiWriter.ensureDeck` correctly
   read that as "cannot prove this deck is ordinary" and reported
   `DECK_COLLISION`. **The Missing Kanji export could not complete against any
   real Anki**, while passing against every mock — the mocks wrote the integers
   the codec accepted. Fractions now decode to a distinct `Json.Frac`, so nothing
   reading an id or counter can be handed one.

Both share a shape worth remembering: a mock written from the same understanding
as the code under test agrees with it, including where that understanding is
wrong.

## The stricter local real-collection gate

The fixture is a *conformance* gate: eight notes, known states, fast, and safe to
run anywhere. It is not a scale gate. Before shipping provider or sync changes,
also run against a **copy** of the real collection at the default 7,000-note
threshold — the same rule the AnkiDroid path follows.

Copy, never point at the original:

```sh
cp "$HOME/.local/share/Anki2/User 1/collection.anki2" /tmp/kani-desktop-gate/
```

Then load that copy as a separate profile in the fixture's base directory, on the
fixture port, and re-run the suite with `-Pkani.liveAnkiDesktopProfile` naming
it. The profile guard is what keeps this honest: the suite refuses to run unless
the loaded profile is the one named, so a copy that was never actually loaded
fails rather than silently qualifying the wrong collection.

A read-only smoke against the operator's already-running session is also useful
for version and capability drift, and is allowed — but only for version,
capability, and configured-read checks, and only with redacted aggregate
evidence. There is deliberately no runner for it here: the qualification suite
itself refuses a non-fixture profile, and the personal-profile smoke stays a
hand-driven read (`--probe version`, `--probe apiReflect`) precisely so no
automated path can grow a write into it. A read-only smoke runner, if one is ever
added, needs a transport-level allowlist that omits every write action, so
automatic archive/repaired post-sync behavior cannot reach a personal profile
even by accident.

## Still outstanding

- The copied-real-collection gate at the 7,000-note threshold has not been run
  on this branch yet.
- The same throwaway-profile smoke needs defining and executing for Windows and
  macOS before Goal 207 completes. Only Linux is covered here.

## Related

- `docs/local-ankidroid-provider-testing.md` — the AnkiDroid equivalent.
- `ci/scripts/run_anki_desktop_fixture.sh` — the fixture launcher.
- `ci/scripts/seed_anki_desktop_kiku_collection.py` — the sanitized collection.
- `CrossProviderSnapshotSpec` — the contract both providers are held to.
