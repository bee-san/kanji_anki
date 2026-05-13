# Suspended Kanji Import

This file defines the suspended-kanji import logic.

Keep this logic in its own file and module.
Do not bury it inside the generic AnkiDroid sync code.
We will probably make it more advanced later.

## Purpose

On sync with AnkiDroid:

1. scan all suspended cards,
2. extract kanji candidates from those cards,
3. import only the kanji that pass a frequency filter,
4. then move the suspended cards out of AnkiDroid and into the app's local archive.

This keeps the active AnkiDroid collection clean while still letting the app study the right suspended trouble kanji.

## Source Of Truth

- AnkiDroid is the source of truth for the active collection.
- The app is the source of truth for the local suspended-card archive after sync.
- The app's suspended-kanji import list is derived from suspended AnkiDroid cards at sync time.

## Frequency Source

Use the Jiten Kanji frequency list.

Source page:

- `https://jiten.moe/other`

Important facts:

- Jiten exposes a dedicated `Kanji` frequency CSV from the frequency downloads page.
- Jiten kanji frequency is expressed as a numeric rank.
- Smaller rank numbers are more common.
- Larger rank numbers are less common.

This means the import rule must be defined in terms of rank, not vague “frequency”.

## Import Rule

Use a configurable Jiten kanji rank cutoff called `X`.

The rule is:

- extract unique kanji from suspended-card expressions,
- look up each kanji in the Jiten Kanji frequency list,
- import the kanji only if its Jiten rank is worse than the cutoff,
- in plain terms: import it only if the numeric rank is greater than `X`.

Example:

- if `X = 500`,
- kanji with rank `120` does not import,
- kanji with rank `900` does import.

This keeps the app focused on less common suspended trouble kanji instead of filling the queue with very common characters.

## Unknown Rank Rule

If a kanji is missing from the Jiten Kanji list, treat it as eligible for import by default.

Reason:

- missing from the ranked list usually means rare, niche, or otherwise outside the common ranked set,
- that is closer to “worth importing” than to “definitely common”.

If this assumption becomes a problem later, change it in this file and module only.

## Scan Scope

The scan should use suspended cards only.

Use these inputs from each suspended card:

- expression text,
- reading,
- meaning,
- note id,
- card id,
- deck/model metadata if useful for future ranking rules.

The first pass should extract kanji from the expression field only.
Do not overcomplicate the first version with reading-based heuristics.

## Normalization Rules

- Normalize the expression text before extracting kanji.
- Deduplicate kanji across all suspended cards in the sync run.
- Keep a link back to the suspended source cards and expressions that caused the import.

The imported kanji record should not be an orphan.
We should always be able to explain which suspended cards pulled it in.

## Sync Order

The sync should conceptually run in this order:

1. read suspended cards from AnkiDroid, plus active cards only when their import filter is enabled,
2. derive suspended-kanji import candidates from the enabled sources,
3. filter them through the Jiten rank cutoff,
4. store suspended cards in the local archive,
5. store imported suspended kanji in the local database,
6. remove those suspended cards from AnkiDroid,
7. rebuild derived dashboard and study inputs from the new local state.

This matters because the import must happen before the suspended cards disappear from AnkiDroid.

## Data We Should Keep Locally

For each imported kanji, keep:

- kanji,
- Jiten rank if known,
- import cutoff used,
- source suspended expressions,
- source suspended card ids,
- first imported at,
- last seen at sync,
- whether the rank was known or unknown.

This gives us enough structure to make the logic smarter later without rethinking the whole pipeline.

## Code Shape

When implemented, this should live in its own code file or module.

Responsibilities of that module:

- extract kanji candidates from suspended cards,
- load and query Jiten kanji ranks,
- apply the cutoff rule,
- return structured import results with provenance.

Responsibilities that should stay outside it:

- generic AnkiDroid provider reads,
- generic database transaction plumbing,
- generic dashboard rebuilding,
- generic study scheduling.

Keep the import logic isolated so it can grow without contaminating the rest of sync.

## First Version Constraints

The first version should stay simple:

- one frequency source,
- one numeric cutoff,
- one extraction path from suspended-card expressions,
- one import decision rule,
- one place in the codebase that owns the logic.

Do not add:

- deck-specific overrides,
- per-model overrides,
- reading-based ranking,
- multi-factor scoring,
- machine-learned prioritization,
- user-tunable heuristics beyond the single cutoff.

If we need those later, this file is where they should start.
