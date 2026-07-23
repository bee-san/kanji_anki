# Missing Kanji AnkiDroid Provider Boundary

Status: Accepted for implementation

Date: 2026-07-23

## Decision

Missing Kanji uses a collection-wide provider boundary that is independent of
Kani's configured Kiku sync. It streams every live note's fields to an
aggregate-only analyzer and never sends these rows through `readCollection`,
sync snapshots, dashboard inventory, or historical sync storage.

Direct Anki creation is available only when the installed provider reports API
specification version 2 or newer and the database permission is granted.
Unsupported or failed capability checks use the UTF-8 CSV destination instead.

## Provider contract

The pinned reference is AnkiDroid 2.24.0:

- `FlashCardsContract.kt` defines the public URIs and columns:
  <https://github.com/ankidroid/Anki-Android/blob/v2.24.0/api/src/main/java/com/ichi2/anki/FlashCardsContract.kt>
- `AddContentApi.kt` defines the supported deck, model, duplicate-query, and
  note insertion sequence:
  <https://github.com/ankidroid/Anki-Android/blob/v2.24.0/api/src/main/java/com/ichi2/anki/api/AddContentApi.kt>
- `CardContentProvider.kt` is the host implementation used to verify query,
  insert, bulk insert, and update behavior:
  <https://github.com/ankidroid/Anki-Android/blob/v2.24.0/AnkiDroid/src/main/java/com/ichi2/anki/provider/CardContentProvider.kt>

Kani supports these provider authorities in order:

1. `com.ichi2.anki.api.provider`
2. `com.ichi2.anki.flashcards`
3. `com.ichi2.anki.debug.api.provider`
4. `com.ichi2.anki.debug.flashcards`

Production authorities require
`com.ichi2.anki.permission.READ_WRITE_DATABASE`; debug authorities require
`com.ichi2.anki.debug.permission.READ_WRITE_DATABASE`.

### Collection read

1. Query `content://<authority>/models` once. Read `_id`, `name`, and
   `field_names`.
2. Prefer paged direct-note queries against
   `content://<authority>/notes_v2`:

   ```text
   selection: id > ?
   selectionArgs: [last_note_id]
   sortOrder: id ASC LIMIT 500
   ```

3. Read `_id`, `mid`, and `flds` from the provider's default projection.
   `flds` uses U+001F separators. A note's model ID maps its arbitrary field
   array to the model metadata loaded in step 1.
4. If `notes_v2` is unsupported, query
   `content://<authority>/notes` with an empty Anki search. This is the
   compatibility path and cannot be paged by the client.

The gateway checks cancellation before metadata access, each page, and each
row. It reports scanned and skipped row counts. It skips a malformed or
unknown-model row and keeps prior valid rows; it does not log the row or field
contents. A null model/direct-SQL cursor is a provider-unavailable error. A
null legacy-search cursor means an empty collection because AnkiDroid returns
null when an Anki search has no matches.

Tags, deck names, card templates, and deleted notes are not queried. Suspended
notes remain in the notes table and are therefore included without a card
queue filter.

### Direct creation

The provider exposes the same operations used by `AddContentApi`:

| Operation | Provider call |
| --- | --- |
| List decks | Query `content://<authority>/decks` |
| Create deck | Insert `deck_name` into `content://<authority>/decks` |
| List models | Query `content://<authority>/models` |
| Create model | Insert model metadata into `content://<authority>/models` |
| Configure template | Update `content://<authority>/models/<mid>/templates/0` |
| Find exported notes | Query `content://<authority>/notes_v2` by `mid` and inspect the stable `SourceId` field |
| Add notes | `bulkInsert` into `content://<authority>/notes?deckId=<did>` |

The dedicated model is `Kani Missing Kanji`, with fields `Kanji`, `Meaning`,
`OnReading`, `KunReading`, `JitenRank`, and `SourceId`. It has one recognition
card. The default deck is `Kani::Missing Kanji`.

Provider spec 1 is read-compatible but is not direct-write-capable for this
feature: upstream documents slow bulk operations and a model-persistence bug.
Kani requires metadata key `com.ichi2.anki.provider.spec >= 2` before enabling
direct creation.

## Idempotency and partial failure

Each export uses `kani-missing:<literal>` in `SourceId` and the
`kani_missing_kanji` tag. Before writing, Kani queries all notes for the
dedicated model and compares exact `SourceId` values. Local export receipts
avoid unnecessary provider work but never replace this external duplicate
check.

Writes use batches of at most 100 notes. Cancellation is checked between
batches. A successful batch is immediately reconciled with the provider before
receipts are recorded. A provider process death, permission loss, null result,
or short bulk-insert result stops the run and reports created, already present,
skipped, and unfinished literals separately. Retry re-queries source IDs and
therefore creates no duplicate notes. Successfully created notes are never
deleted to simulate rollback.

If the requested deck or model name already exists, it is reused only when its
shape exactly matches Kani's contract. An incompatible model causes a
capability error and offers CSV; Kani does not rewrite user models. An existing
deck is safe to reuse because notes are additive and the writer never changes
deck options or scheduling state.

## Failure mapping

| Condition | Product state |
| --- | --- |
| No supported authority | AnkiDroid not installed |
| Permission denied/lost | Permission required |
| Provider lock/process death/null direct cursor | Retryable provider unavailable |
| Cancellation | Cancelled; prior successful inventory/export remains |
| Malformed inventory row | Count and skip; continue |
| Too many malformed rows | Complete with warning; UI offers rescan |
| Provider spec below 2 | Read enabled, direct write disabled, CSV offered |
| Model collision or incomplete write capability | Direct write disabled for this run, CSV offered |

The warning threshold for an inventory is the lower of 100 rows or 1% of rows
seen, with a minimum threshold of one malformed row. A scan above the threshold
may still publish its aggregate set but must display the warning and skipped
count.

## Fake-provider evidence

`AnkiDroidCollectionInventoryGatewayInstrumentedTest` verifies:

- two note types with different field counts;
- active and suspended note rows;
- duplicate text, HTML, entities, ruby, sound markers, and arbitrary fields
  crossing the boundary without persistence;
- direct-SQL and legacy-search paths;
- cooperative cancellation after a partial scan;
- malformed-row isolation and explicit provider errors; and
- read/write capability status for a spec-2 test provider.

The fake provider does not authorize production writes in this design-only
change. Writer mutation coverage is added with the writer implementation.

## Live/emulator evidence

The source contract above was inspected at the pinned AnkiDroid 2.24.0 tag.
The release gate will run `ci/scripts/run_local_ankidroid_fixture.sh` against
the sanitized fixture after the inventory and writer instrumentation subsets
are present. It must record:

- provider authority and spec version;
- collection-wide note/model counts;
- a disposable `Kani::Missing Kanji` deck/model/note create, duplicate retry,
  and cleanup; and
- confirmation that normal configured-model sync still passes.

No personal note text, model names, or deck names may appear in the evidence.

## Consequences

- Collection discovery cannot change configured-model sync, import,
  retirement, or scheduler behavior.
- Raw note fields exist only for the duration of one callback.
- The direct writer expands Kani's provider surface, but only for dedicated
  additive notes. It still never updates existing user notes or Anki scheduling
  columns.
- CSV remains a complete destination, not merely an error message.
