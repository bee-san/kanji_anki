# Anki Browser Query Import Plan

This is a planning document only. Do not implement this from this file until a
separate implementation pass is requested.

The goal is to let Kani import practice candidates from cards matched by an
Anki browser query, while keeping the current suspended-only default intact.
Active-card import must remain opt-in. Browser-query import must also be
explicitly enabled by the user.

## Current State

Kani already has an import-filter system under Settings > Anki source >
Import filters.

The current import sources are:

- Active cards.
- Suspended cards.
- Tagged cards.
- Weak cards.

The current defaults should stay:

- Active cards: off.
- Suspended cards: on.
- Tagged cards: off.
- Weak cards: off.

The relevant code paths today are:

- `Records.Settings` in `core/src/main/java/dev/bee/kanjianki/core/Records.java`
  owns the settings values used by core logic.
- `SyncSettings.fromStore(...)` in
  `app/src/main/java/dev/bee/kanjianki/sync/SyncSettings.java` loads persisted
  settings from `LocalStore`.
- `MainActivity.importFilterSettingsPanel(...)` in
  `app/src/main/java/dev/bee/kanjianki/MainActivity.java` renders and saves
  the Import filters screen.
- `AnkiDroidGateway.readCollection(...)` in
  `app/src/main/java/dev/bee/kanjianki/anki/AnkiDroidGateway.java` reads notes
  and cards from the AnkiDroid provider.
- `KanjiImportSelector.importFrom(...)` in
  `core/src/main/java/dev/bee/kanjianki/core/KanjiImportSelector.java` decides
  which cards count as import evidence.
- `ManualSyncEngine.runLocked(...)` in
  `app/src/main/java/dev/bee/kanjianki/sync/ManualSyncEngine.java` ties provider
  reads, import selection, dashboard rebuilds, local archive persistence, and
  study seeding together.

The browser-query feature should extend this architecture instead of replacing
it.

## Product Goal

Users should be able to type an Anki browser query in Kani settings and enable
it as an import source.

Example queries:

- `deck:Japanese tag:kani`
- `tag:leech`
- `rated:30:1`
- `prop:lapses>2`
- `is:suspended tag:kanji`
- `deck:Mining -tag:kani_archived`

When browser-query import is enabled, Kani should ask AnkiDroid for notes that
match the configured query, read the cards for those notes, and let matching
cards seed Kani practice candidates through the same rank range, source-count,
and provenance pipeline as the existing import filters.

The feature is not a generic Anki browser replacement. It is a focused import
source selector.

## Non-Goals

Do not implement these in the first version:

- Multiple saved browser queries.
- Query syntax validation that fully understands Anki's grammar.
- A live count preview that queries AnkiDroid while typing.
- Full desktop Anki integration.
- Per-query scheduling policies.
- Importing non-Kiku note types without the existing note-type mapping.
- Broad JMdict or vocabulary import.
- A new side queue or another SRS.
- Mutating mature Anki card review data directly.

## User Experience

Add a new section inside Settings > Anki source > Import filters.

Suggested controls:

- Checkbox: `Browser query`
- Text input label: `Anki browser query`
- Input hint: `deck:Japanese tag:kani`
- Helper text:
  `Import cards matched by this Anki browser query. Kani still applies the
  selected note type, rank range, and matching-card threshold.`
- Save button remains `Save import filters`.

The visible default state on fresh install should be:

- Active cards: unchecked.
- Suspended cards: checked.
- Tagged cards: unchecked.
- Weak cards: unchecked.
- Browser query: unchecked.
- Anki browser query input: empty.

If browser-query import is unchecked, the query text can be preserved but must
not affect sync.

If browser-query import is checked and the query text is blank, saving should
be blocked with a short message:

`Enter an Anki browser query or turn off Browser query.`

If all import sources are disabled, saving should still be blocked by the
existing rule:

`Turn on at least one import source.`

## Settings Model

Add two persisted settings:

- `import_browser_query_cards`
- `import_browser_query`

Suggested defaults:

- `Records.DEFAULT_IMPORT_BROWSER_QUERY_CARDS = false`
- `Records.DEFAULT_IMPORT_BROWSER_QUERY = ""`

Add fields to `Records.Settings`:

- `boolean importBrowserQueryCards`
- `String importBrowserQuery`

Add helpers:

- `browserQueryImportEnabled()`
  - returns true only when `importBrowserQueryCards` is true and
    `importBrowserQuery.trim()` is non-empty.
- `normalizedBrowserQuery()`
  - trims the query.
  - collapses leading/trailing whitespace only.
  - does not rewrite internal query syntax.

Update `hasImportSourceEnabled()` so browser-query import counts as a source
only when enabled and non-blank.

The setting should be backward compatible with existing `Records.Settings`
constructor shapes. Existing tests in `RecordsValueCoverageTest` and
`LearningStepSettingsTest` should be extended rather than weakened.

## Persistence Rules

`SyncSettings.fromStore(...)` should load the two new settings from
`LocalStore`.

Fresh install behavior:

- Browser-query import is off.
- Browser query is empty.
- Suspended cards remain on.
- Active cards remain off.

Existing install behavior:

- Users with no browser-query keys get the fresh default.
- Existing import source choices are preserved.
- The old-default migration for active/suspended import must not turn browser
  query on.
- If a user later types a query and turns the source off, the query text can
  remain saved for convenience.

Invalid values:

- Any non-`1` integer for `import_browser_query_cards` should behave as false.
- Null query should load as empty string.
- A whitespace-only query should behave as disabled even if the checkbox key is
  `1`.

## Query Semantics

The browser query must be combined with the configured note type.

Kani should not let a browser query bypass the configured note-type and field
mapping constraints. If the user configured the `Kiku` note type, the provider
search should only return Kiku notes, even when the user query is broad.

Recommended search shape:

```text
note:"<settings.modelName>" (<user query>)
```

If AnkiDroid search syntax does not support parentheses in the provider query,
use the safest equivalent that preserves both constraints. The implementation
must be tested against the fake provider and, for release, against real
AnkiDroid.

The raw user query should not be silently modified beyond trimming. Escaping the
note-type name remains the app's responsibility.

Important edge cases:

- Empty query: disabled.
- Query with only whitespace: disabled.
- Query that matches no notes: sync succeeds with no query-sourced imports.
- Query that matches archived `kani_archived` notes: those notes must still be
  filtered out by the existing archived-tag guard.
- Query that matches another note type: those notes must be ignored.
- Query that matches notes with unsupported card template ords: keep the
  existing template validation behavior.
- Query that matches active cards: import them only because browser-query import
  is enabled, not because active-card import is globally enabled.
- Query that matches suspended cards: preserve suspended-card archive behavior.

## Provider Read Design

Today `AnkiDroidGateway.queryNotes(...)` reads all configured-note-type notes by
searching:

```text
note:"<settings.modelName>"
```

The browser-query feature needs an additional provider search, but it should not
force the whole app to read every active card when only a narrow query is
enabled.

Recommended first implementation:

1. Keep the base configured-note read path intact.
2. Add a second note search when browser-query import is enabled.
3. Merge browser-query notes with the base note map by note id.
4. Track which note ids matched the browser query.
5. Read cards for the union of note ids.
6. Attach query-match provenance to the returned snapshot or a parallel
   selection structure.

This requires a design decision: how `KanjiImportSelector` should know which
cards matched the browser query.

Preferred option:

- Extend `Records.Card` with a `boolean browserQueryMatched` flag, preserving
  old constructors with a default of false.
- `AnkiDroidGateway` marks cards whose note id came from the browser-query
  search.
- `KanjiImportSelector.sourceMatch(...)` treats a card as matching when:

```java
settings.browserQueryImportEnabled() && card.browserQueryMatched
```

Why this is preferred:

- It keeps source selection in core logic.
- It avoids adding app-only side maps to `ManualSyncEngine`.
- It keeps fake gateway tests simple.
- It lets provenance live with the card evidence being evaluated.

Alternative option:

- Add a new wrapper type such as `Records.CollectionSnapshotWithSelection`.
- Pass browser-query matched note ids separately.

This is more explicit but creates more churn across existing tests and gateway
interfaces.

## Source Type And Provenance

Add a new source type constant:

- `Records.SOURCE_BROWSER_QUERY = "browser_query"`

When a card matches multiple sources, source-type behavior needs to be stable.

Recommended precedence:

1. `suspended`
2. `browser_query`
3. `active`

Tagged and weak matches are not physical card states. They should continue to
force practice, but source type should describe where the source evidence came
from.

For query-matched active cards:

- `sourceType = "browser_query"`
- `suspended = false`
- `forcePractice = true`

For query-matched suspended cards:

- `sourceType = "suspended"`
- `suspended = true`
- `forcePractice = true`

This keeps suspended archive behavior easy to reason about. A query that
selects suspended cards should still archive only the selected suspended cards
after the local archive is written.

Add a future optional detail field only if needed:

- `matchedBrowserQuery`
- `browserQueryText`

Do not store raw query text in every source row in the first version unless the
UI needs it. The setting value is enough for most explanation surfaces.

## Import Selection Rules

`KanjiImportSelector.sourceMatch(...)` should become:

- active match: active import is enabled and card is not suspended.
- suspended match: suspended import is enabled and card is suspended.
- tagged match: tagged import is enabled and note has one configured tag.
- weak match: weak import is enabled and card crosses weak thresholds.
- browser-query match: browser-query import is enabled and card was matched by
  the query search.

Any enabled source can select a card.

`forcePractice()` should be true when the match is one of:

- suspended.
- tagged.
- weak.
- browser query.

Active-only imports should remain non-force-practice unless the user enabled a
more targeted source that also matched the card.

The existing `importMinMatchingCardsPerKanji` threshold should apply after
browser-query matching, just like all other import sources.

The existing Jiten rank range should apply after browser-query matching, just
like all other import sources.

## Local Archive Behavior

The browser-query feature must not archive active cards.

Archive behavior should remain:

- Only suspended cards can be archived locally as suspended-card imports.
- Only suspended cards included in selected suspended imports are eligible for
  provider cleanup/tagging.
- Active cards selected by browser query can seed practice, but they remain in
  AnkiDroid untouched.

If the browser query selects suspended cards while the `Suspended cards`
checkbox is off, decide explicitly whether those suspended cards are archived.

Recommended behavior:

- If a query-selected card is suspended, archive it, because suspended-card
  local archive is the safe storage boundary before provider cleanup.
- Provider cleanup should only affect suspended cards that actually contributed
  selected import evidence.

Rationale:

- The user explicitly asked Kani to import cards matching the query.
- If those cards are suspended, they need the same safe local preservation path
  as normal suspended imports.
- This avoids a confusing case where a suspended query match seeds practice but
  is not archived.

This behavior must be documented in Settings helper text if implemented:

`Suspended cards matched by the query are archived locally before Kani tags them
in AnkiDroid.`

## UI Details

Keep the current grouped expandable Settings style.

Suggested Import filters ordering:

1. Short summary line from `settingsImportSummary(...)`.
2. Source checkboxes:
   - Suspended cards.
   - Browser query.
   - Active cards.
   - Tagged cards.
   - Weak cards.
3. Browser query input.
4. Tags input.
5. Weak thresholds.
6. Minimum matching cards.
7. Save button.

The order above puts the recommended source first and places the browser query
near source selection. If preserving the current visual order is lower-risk,
append Browser query after Weak cards for the first implementation.

`settingsImportSummary(...)` should include query state:

- Suspended-only default: `suspended; 1 matching card per kanji`
- Browser query enabled: `suspended + query; 1 matching card per kanji`
- Query only: `query; 1 matching card per kanji`

Use the short label `query` in the summary, not the full query text.

Never show the full query in the hero pill if it can be long. Long browser
queries can break layout on mobile.

## Sync Progress And Status Copy

No new sync stage is required for the first version.

Existing stages can cover the work:

- `READING_NOTES` includes provider query reads.
- `SCANNING_CARDS` includes cards for matched notes.
- `PROCESSING_IMPORTED_CARDS` includes source filtering.

Optional future copy:

- During `READING_NOTES`, show `Reading AnkiDroid notes`.
- Do not show the raw browser query in sync progress.

Sync status headline should not imply active cards are broadly checked by
default. Keep copy aligned with suspended-default behavior.

## Data Model Changes

Likely code changes:

1. `Records.Card`
   - Add `browserQueryMatched`.
   - Preserve all existing constructors.
   - Add a full constructor overload if needed.

2. `Records.Settings`
   - Add fields and defaults.
   - Update constructor parsing for full settings shape.
   - Update `kikuDefaults()`.
   - Update `hasImportSourceEnabled()`.

3. `Records.SuspendedSourceDetails` or `Records.SuspendedSource`
   - Add no new field for first version unless tests reveal source type is not
     enough.
   - Add `SOURCE_BROWSER_QUERY`.

4. `SyncSettings`
   - Add setting keys.
   - Load and normalize query setting.
   - Save through `MainActivity`.

5. `LocalStore`
   - No schema migration needed if settings are stored in the generic settings
     table.
   - Add tests that settings persist across store instances.

6. `AnkiDroidGateway`
   - Add browser-query note search.
   - Merge note maps.
   - Track query-matched note ids.
   - Mark cards as query matched.
   - Keep archived-tag filtering.
   - Keep SQL fallback for base note read.

7. `FakeAnkiDroidProvider`
   - Teach fake note search to respond to browser-query strings used in tests.
   - Include cases for no match, other note type, active match, suspended match,
     and archived tag.

8. `KanjiImportSelector`
   - Add browser-query source matching.
   - Add source type and force-practice behavior.
   - Keep source-count threshold and rank filtering unchanged.

9. `ManualSyncEngine`
   - Ideally no behavior changes beyond receiving richer snapshots.
   - Confirm `suspendedImportsOnly(...)` includes query-selected suspended
     sources.

## Provider Query Construction

Add a small helper in `AnkiDroidGateway`, for example:

```java
private String configuredModelSearch(Records.Settings settings) {
    return "note:\"" + escapedSearchValue(settings.modelName) + "\"";
}

private String configuredBrowserQuerySearch(Records.Settings settings) {
    return configuredModelSearch(settings) + " (" + settings.normalizedBrowserQuery() + ")";
}
```

If escaping or parentheses are risky against AnkiDroid's provider, test and
adjust before release.

At minimum, model names containing quotes should not produce broken search
strings. A conservative first pass can reject model names or queries that cannot
be safely expressed, but that should produce a clear config error rather than
silently broadening the search.

Do not build provider selection strings with unbounded SQL assumptions. The
AnkiDroid notes provider treats selection as browser-search text for the current
code path, not as app-owned SQL.

## Error Handling

Browser-query provider errors should be classified carefully.

Recommended first behavior:

- If the base note read works but the browser-query read throws because the
  query syntax is invalid, return a permanent/config sync error.
- The error message should tell the user to fix the browser query.
- Do not silently ignore query failures when browser-query import is enabled.

Suggested message:

`AnkiDroid could not run the browser query. Check the query in Import filters.`

If possible, append the provider's short error message.

Do not include the full query in crash logs or long error messages unless it is
already visible in the user's own settings screen. Browser queries may contain
private deck or tag names.

If browser-query import is disabled, query text should never be sent to
AnkiDroid and cannot fail sync.

## Privacy And Safety

Browser queries can contain private deck names, tags, project names, or personal
mnemonics.

Rules:

- Store the query locally only.
- Do not send it anywhere outside AnkiDroid's local provider.
- Do not include the full query in analytics, logs, sync history headlines, or
  crash messages.
- If the query is shown in Settings, keep it inside the user's editable field.

The app currently has no backend. Keep it that way for this feature.

## Interaction With Existing Filters

The browser-query source should combine with existing filters using the current
any-source logic.

Examples:

1. Default settings:
   - Suspended on.
   - Browser query off.
   - Result: suspended-only import.

2. Browser query only:
   - Suspended off.
   - Browser query on with `tag:kani`.
   - Result: cards matched by `tag:kani` can seed practice.

3. Suspended plus browser query:
   - Suspended on.
   - Browser query on with `tag:leech`.
   - Result: suspended cards and query-matched cards can seed practice.

4. Browser query plus minimum matching cards = 2:
   - A kanji must appear in at least two selected source cards, regardless of
     whether those cards came from query, suspended, tagged, or weak matching.

5. Query matches active mature card:
   - It can be used as content if the query source is enabled.
   - It must not mutate mature-card Anki review data directly.

## Testing Plan

### Core Unit Tests

Add tests in `RecordsValueCoverageTest`:

- Defaults keep browser query off and empty.
- Full settings constructor accepts browser-query values.
- Whitespace-only query behaves as disabled.
- `hasImportSourceEnabled()` returns true for nonblank enabled query.
- `hasImportSourceEnabled()` returns false when query checkbox is true but text
  is blank and all other sources are off.

Add tests in `KanjiImportSelectorTest`:

- Default settings ignore active non-suspended cards.
- Browser-query enabled imports an active card marked `browserQueryMatched`.
- Browser-query disabled ignores a card marked `browserQueryMatched`.
- Browser-query enabled with blank query ignores marked cards.
- Query-matched active source has `sourceType = browser_query`.
- Query-matched active source has `forcePractice = true`.
- Query-matched suspended source stays `sourceType = suspended`.
- Query-matched suspended source is included in suspended imports.
- Rank range still filters query-matched cards.
- Minimum matching card threshold counts query-matched cards.
- Duplicate source card ids dedupe correctly when a card matches query and
  another source.

### App Unit Tests

Add tests around `AnkiDroidGateway.selectRequiredFields(...)` only if settings
changes affect required fields. Browser query should not change required fields.

### Instrumented Store Tests

Extend `LocalStoreInstrumentedTest`:

- Browser-query settings persist across store instances.
- Missing browser-query keys load defaults.
- Blank query persists but behaves as disabled in `SyncSettings.fromStore(...)`.
- Existing old-default import migration does not enable browser query.
- Customized import filters with browser query remain customized.

### Instrumented Settings UI Tests

Extend `MainActivityInstrumentedTest`:

- Fresh Settings > Import filters renders Browser query unchecked.
- Fresh query text field is empty.
- Saving with Browser query checked and blank text shows validation toast.
- Saving with Browser query checked and nonblank text persists:
  - `import_browser_query_cards = 1`
  - `import_browser_query = <trimmed query>`
- Turning Browser query off preserves query text but disables source.
- Summary pill shows `query` only when enabled and nonblank.

### Provider Contract Tests

Extend `AnkiDroidGatewayProviderInstrumentedTest` and
`FakeAnkiDroidProvider`:

- Query import reads active note matched by fake query.
- Query import reads suspended note matched by fake query.
- Query import does not include unmatched notes.
- Query import still filters out archived notes.
- Query import ignores notes from a different note type.
- Invalid query produces config error when query import is enabled.
- Disabled query source does not call the fake query search path.

### Manual Sync Tests

Extend `ManualSyncEngineInstrumentedTest`:

- Browser-query active card creates dashboard row without archiving a suspended
  card.
- Browser-query suspended card creates a suspended import and provider cleanup
  receives that selected suspended import.
- Browser-query plus default suspended source merges imports without duplicate
  source rows.
- Adaptive workload still applies after query-sourced rows are produced.

### Live AnkiDroid Test Before Release

Because this changes provider search behavior, a release-quality implementation
must be validated against real AnkiDroid in an emulator.

Minimum live scenario:

1. Install real AnkiDroid.
2. Load a throwaway collection with Kiku notes.
3. Add a tag such as `kani_query_test` to one active Kiku card.
4. Suspend one Kiku card with a different kanji.
5. Configure Kani:
   - Suspended cards on.
   - Browser query on.
   - Query: `tag:kani_query_test`.
6. Run sync.
7. Verify:
   - Suspended card imports as before.
   - Query-tagged active card imports only because query source is enabled.
   - Active card is not archived/tagged as `kani_archived`.
   - Selected suspended card is safely archived before provider cleanup.

## Acceptance Criteria

The feature is complete only when all of these are true:

- Fresh installs remain suspended-only by default.
- Existing users' customized import filters are preserved.
- Browser-query import is opt-in.
- A blank browser query cannot accidentally select cards.
- A broad browser query cannot bypass the configured note type.
- Query-matched active cards can seed Kani practice without being archived.
- Query-matched suspended cards are archived locally before provider cleanup.
- Rank range and minimum matching card thresholds apply to query matches.
- Settings UI clearly shows the source as optional.
- Tests cover core selection, settings persistence, UI defaults, provider query
  behavior, and manual sync integration.
- Real AnkiDroid provider behavior has been checked before release.

## Suggested Implementation Order

1. Add settings fields and defaults in `Records.Settings`.
2. Add persistence keys and load/save behavior in `SyncSettings` and
   `MainActivity`.
3. Add UI validation for blank enabled query.
4. Add `browserQueryMatched` to `Records.Card` with backward-compatible
   constructors.
5. Add query search and note-id tracking in `AnkiDroidGateway`.
6. Add `SOURCE_BROWSER_QUERY` and source matching in `KanjiImportSelector`.
7. Adjust archive selection for query-matched suspended cards.
8. Add fake-provider support.
9. Add core and instrumented tests.
10. Run JVM tests and Android test compilation.
11. Run focused instrumented tests on emulator.
12. Run live AnkiDroid provider validation before release.

## Rollback Plan

If the provider query path proves unreliable:

- Keep the settings hidden behind disabled code until provider behavior is
  confirmed.
- Do not remove suspended-only defaults.
- Leave existing active/suspended/tagged/weak filters intact.
- Revert only the browser-query source matching and provider query code.

If UI validation causes problems:

- Keep the settings keys and core defaults.
- Temporarily hide the Browser query controls.
- Preserve any saved query text so users do not lose local configuration.

## Open Questions

- Does AnkiDroid's provider reliably accept parentheses in note search
  selection strings?
- Should query-matched suspended cards be archived when the Suspended cards
  checkbox is off? This plan recommends yes, but the implementation should make
  that behavior explicit in tests and copy.
- Should browser-query imports get their own visible reason text in kanji detail,
  such as `Imported from browser query`?
- Should query text be exported in any future settings backup? If so, make it
  clear that browser queries may contain private deck/tag names.
- Should the first version allow only one query, or should the data model be
  shaped for multiple named queries later? This plan recommends one query now.
