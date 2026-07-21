# Simple

This is the restart brief for a significantly simpler Kani app.

The next generation should optimize for one product:

- sync with AnkiDroid,
- give me one obvious button to study,
- show me a useful kanji details page,
- update itself from GitHub,
- do those four things reliably.

Everything else is secondary.

## The Core Product

Build an Android-first kanji companion for one person on one device.

It should:

1. read the live AnkiDroid collection,
2. derive weak-kanji targets from that collection,
3. pull suspended cards out of AnkiDroid and keep them in a local archive,
4. run a small bridge SRS for those kanji,
5. explain each kanji in context,
6. ship updates without manual sideload hunting.

Do not make it a second full flashcard system.
Do not make it a general Anki browser.
Do not make it depend on fake data or frozen fixtures at runtime.

## What The Current Repo Learned

The most important architectural lesson is the boundary:

- AnkiDroid is the source of truth for the active collection.
- Our app owns a local mirror of active cards, a local archive of suspended cards, derived kanji analysis, study queue/history, and sync history.
- Derived analysis must be rebuildable from the local mirror.
- Study state must stay separate from source sync state.

That separation is worth keeping.

The other major lesson is what not to repeat:

- the repo now has Python server logic and Android logic for the same product rules,
- that created parity work, migration fixtures, and duplicated scheduler behavior,
- if we start again, we should keep one real runtime for the product.

Given the fundamental features, that runtime should be Android.

## 1. Sync With AnkiDroid

This should be the foundation.

### Simple version

- Sync from AnkiDroid's exported content provider, not from demo data.
- Fail closed when AnkiDroid is missing or permission is missing.
- Show an explicit empty state with instructions instead of pretending the app has data.
- Validate note model names and field mappings up front.
- Treat model/field mismatch as a permanent configuration error, not a retryable glitch.
- Treat provider timeouts and transient read failures as retryable.
- Suspended cards should be imported during sync, removed from AnkiDroid, and kept in the local database.
- Suspended-kanji import rules should live in a separate file and module, not be buried inside generic sync code.
- The local database should clearly distinguish active mirrored cards from locally archived suspended cards.
- Keep a local mirror of active notes/cards, plus the suspended-card archive, derived dashboard rows, and sync history.
- Record sync runs with status, counts, timestamps, and error messages.
- Handle deleted notes/cards cleanly instead of assuming the collection only grows.
- Make manual sync the required path.

### Not part of the simplified app

- Background sync is out of scope.
- Polling is out of scope.

### Main lesson

- Honest empty states are better than fake success.
- Permission and provider status are product features, not edge cases.
- Sync code should be boring, inspectable, and idempotent.
- Sync is also the boundary where suspended cards move from AnkiDroid into the app's own archive.

## 2. Suspend Feature

Suspension is part of the product, not just a background metric.

### Product rules

- The app should make it easy to move trouble cards out of AnkiDroid and into the local suspended archive.
- Suspended cards are one of the core signals that drive weak-kanji analysis.
- Suspended kanji should be imported through a dedicated frequency-filtered rule set defined in [suspended-kanji-import.md](suspended-kanji-import.md).
- The app should show clearly which examples are in the local suspended archive and which are still active in AnkiDroid.
- Once a card has been archived as suspended, the app owns that suspended state locally.
- Sync should empty suspended cards out of AnkiDroid and preserve them in the local database.
- If the app supports bringing a card back, that restore action should be explicit and deliberate.
- Dashboard, details, and study state should all be able to use the local suspended archive directly.

### Main lesson

- Suspension is not just data input. It is part of the workflow.
- A good simplified app should help you move trouble cards out of the active Anki loop, not just inspect them.

## 3. One Button Study

The UI should have one primary action: `Study now`.

Internally the system can still distinguish new, learning, and review work, but the product should not make the user manage that complexity.

### Product rules

- This is a bridge SRS for weak kanji.
- It is not a second full kanji curriculum.
- It is not a clone of the user's whole Anki workload.
- seed the queue from collection-derived problem kanji, especially trouble that shows up in the local suspended archive,
- keep the live queue capped,
- keep new introductions capped,
- mix writing and recognition,
- re-test new or failed items after a short delay,
- use spaced repetition for longer-term follow-up,
- retire items once real Anki support is strong enough.

### Recommended SRS shape

This is the guide I would pass on.
It is a strong default, not a sacred law.

For new or lapsed kanji, start with a short bridge packet:

1. acquisition cue with writing,
2. immediate confusable recognition check,
3. one short scheduled follow-up after roughly 10 minutes,
4. then hand the item to longer-term spaced review.

For longer-term review:

- Use an FSRS-style retention-based scheduler.
- A 90% target retention is a good default.
- Store stability and difficulty explicitly.
- Rotate review tasks so writing stays in the loop without owning every mature rep.

Default operating values:

- active queue cap: around 20-25 items,
- first-time introductions: around 3 per day,
- review outcomes: keep them simple and fast.

### Writing contract

Writing should be a first-class contract, not a UI afterthought.

- The session payload should explicitly say whether writing is required.
- Failed writing should cap the allowed rating to `again` until the user passes or manually overrides it.
- A good guide ladder is: Trace -> Outline -> Minimal hints -> Blind recall.
- Guided evaluation is great when stroke geometry exists.
- Manual override must still exist for ambiguity or missing geometry.
- Duplicate review protection matters. A session token or equivalent safeguard should stop double-submits from advancing the item twice.

If simplicity is the goal, a clean next review cycle could be:

- context production,
- confusable recognition,
- sampled handwriting.

## 4. Kanji Details Page

The detail page should support memory, not just display facts.

### Minimum useful contract

For every cached problem row, the detail page should be able to show:

- kanji,
- keyword or primary meaning,
- reading,
- browser search string back into Anki,
- a few real collection examples,
- a short explanation of why this kanji is in the queue,
- clear local suspended vs active examples,
- an action to archive or restore where it is appropriate.

### Outside the simplified app

- fuller meaning lists,
- separate on and kun readings,
- components and component hints,
- stroke count and stroke order,
- split example groups such as suspended, bridge, and mature,
- explicit support metrics such as support deficit.

### The most important lesson here

The detail page must be collection-derived first.

The Android code learned the right lesson:

- do not pin the page to fixture examples,
- derive details from the synced collection itself,
- dictionary and stroke-order assets are enhancements, not the only source of usefulness.

### What the server version adds

The Python server version also shows the value of richer assets:

- KANJIDIC-style meanings/readings,
- KanjiVG stroke data,
- component hints,
- a clearer explanation of why the kanji is in the queue.

Those are good upgrades, but they should sit on top of a collection-derived base.

### Main lesson

- The detail page should answer “why am I studying this?” as clearly as “what does this mean?”
- Real example words are more valuable than decorative metadata.
- If external dictionary assets fail, the page should still be useful.

## 5. Auto Updating Via GitHub

The update path should be boring and reliable.

### Simple version

- Build a signed release APK.
- Tag releases as `vMAJOR.MINOR.PATCH`.
- Publish the APK to GitHub Releases.
- Publish a SHA-256 checksum beside it.
- Let the app check the latest public release, download the APK, and launch the installer flow.
- Verify the checksum before install.

### Constraints to keep

- The release feed must be public if the app is checking it anonymously.
- If the source repo stays private, use a public mirror repo for releases.
- The app must handle the Android “install unknown apps” permission cleanly.
- Signing secrets must stay outside the repo.
- Release automation should fail if signing material is missing. Do not silently publish unusable builds.

## Shape Of The Simplified App

- Android app with local database, honest empty state, and manual sync.
- Live AnkiDroid sync with permanent/transient failure handling.
- Suspended-card archive populated from sync and owned locally.
- Collection-derived dashboard rows and kanji detail pages.
- One-button study with writing + recognition and the bridge/FSRS core.
- GitHub release updater with checksum verification.
- No background sync.
- No parity tooling in the product runtime.
- No extra metadata unless it clearly earns its place.

## Minimal Data Model

Keep the schema small and explicit:

- settings
- sync_runs
- source_notes
- source_cards
- derived_problem_kanji_rows
- study_items
- study_review_log

Everything else should justify its existence.

## Non-Goals

Things I would avoid in the simplified app:

- separate Python and Android implementations of the same study logic,
- runtime fixture fallbacks,
- manual tagging workflows for weak kanji,
- a huge always-growing bridge queue,
- turning the app into a full Anki replacement,
- hiding sync/configuration failure behind vague generic errors.

## Final Teaching

If I had to pass only a few principles on, they would be these:

- Keep AnkiDroid as the source of truth.
- Keep our cache, analysis, and study state separate.
- Fail honestly.
- Keep AnkiDroid in charge of the active collection.
- Keep suspended cards in the app's own local archive.
- Make archiving and restoring part of the real workflow.
- Make `Study now` feel effortless.
- Keep the product centered on writing + recognition.
- Keep the SRS small, bridge-shaped, and tied to real Anki support.
- Keep the simplified version focused on writing + recognition.
- Make the detail page explain the memory problem, not just the kanji.
- Keep releases boring, signed, and updateable.
- Pick one production runtime and own it.

Colour scheme should be pink girlypop
