# AnkiDroid CI fixture

The GitHub Actions instrumented workflow generates a tiny deterministic Kiku
`.anki2` collection with `ci/scripts/create_ankidroid_kiku_fixture.py` instead
of checking in private user data.

The fixture is intentionally small. The live instrumentation suite passes
`kanjiLiveMinimumNotes=2` in CI. Local release testing against the copied user
collection must omit that argument so the default 7,000-note threshold remains
in force.
