# Similar Kanji “Explore the differences” copy and data rules

Captured: 2026-06-17
Task: `t_6fe73510`
Scope: content/spec only; no UI implementation or new language-data source is included here.

## Screenshot evidence

Screenshot: `/Users/autumnskerritt/.hermes/webui/attachments/24fb9ef3b86e/Screenshot_20260617_073536_Kani.jpg`

Visible result:

- Prompt: `Which kanji means Loss of strength exhaustion weakness?`
- Correct kanji: `脱`
- Reading shown: `だつりょく`
- Source word shown: `脱力`
- Individual kanji meanings shown: `Undress, removing`

Data distinction: `loss of strength / exhaustion / weakness` is the word-level meaning for `脱力` from the card/source evidence. The bundled KANJIDIC2 row for standalone `脱` contains meanings such as `undress`, `removing`, `escape from`, `get rid of`, `be left out`, and `take off`. Do not write copy that implies `脱` alone literally means “loss of strength”.

The screenshot’s exact distractor set could not be recovered from `similar_kanji.tsv`. Current direct similar rows for `脱` are `悦`, `税`, `説`, and `鋭`; the visible screenshot distractors appear to include `称`, `吐`, and an ambiguous selected wrong glyph, which are not direct `脱` rows in the checked TSV. Use the screenshot as a `脱` / `脱力` prompt case, not as evidence for exact pair explanations involving those visible distractors unless local session-state data supplies the choices.

## data_sources

1. `app/src/main/res/raw/similar_kanji.tsv`
   - Verified: 2,053 non-comment pair rows.
   - Provenance in file/docs: generated from Kiku `apps/docs/public/_kiku_db_main.tar`, Kiku commit `68c4d00e8c2d8a61f8714c8c8075b1451ca3f77d`; Kiku `visuallySimilar` values are populated from WaniKani kanji pages.
   - Safe claim: pair membership only, e.g. “Kani has `脱` and `説` in its visually similar source list.”
   - Not safe: explaining why the pair is visually similar; no reason/component field exists in the TSV.

2. `app/src/main/res/raw/kanji_strokes.tsv`
   - Verified: 6,526 non-comment stroke-guide rows.
   - Provenance: compact normalized stroke guides generated from KanjiVG source `kanjivg-20250816.xml.gz`; attribution file cites KanjiVG CC BY-SA 3.0, release `r20250816`.
   - Safe claim now: display/animate side-by-side guide shapes; count strokes from pipe-delimited records; compare counts when checked against the dictionary stroke count.
   - Conditional future claim: “extra/missing stroke” or “longer/shorter stroke” only after a tested algorithm maps comparable strokes and records confidence. The compact TSV does not import KanjiVG component/group labels.

3. `app/src/main/assets/dictionaries/kanji_dictionary.db`
   - Verified: 13,108 `kanji` rows.
   - Verified DB SHA-256 matches sidecar: `bbcdd7788299f32dc689d4f88c65d495738b3d0f4feadc632956dc423b18ad19`.
   - Provenance: KANJIDIC2 meanings/readings/stroke count/grade/radical/frequency plus Jiten rank; KANJIDIC2 is CC BY-SA 4.0 via EDRDG.
   - Safe claim: standalone kanji meanings, on/kun/nanori readings, dictionary stroke count, grade, radical number, frequency/rank.
   - Not safe: word-level meanings. `documentation/dictionary_sources.md` says word-level dictionary data is not bundled; study `From:` lines come from Anki examples.
   - Caution: the `radical` field is a primary radical number, not a full component decomposition.

4. Local inventory / imported Anki evidence
   - Relevant model fields in `RecordsImportModels.KanjiInventoryItem`: `kanji`, `primaryMeaning`, `readings`, `browserSearch`, source/example counts, suspension/last-seen metadata.
   - Safe claim: current prompt/source word labels such as `From: 脱力`, and source-card readings/meanings when already in the local study session.
   - Caution: user/imported Anki examples can be private. Keep them local; do not publish or upload them.

5. Current explanation model
   - `SimilarKanjiExplanationPolicy` already supplies conservative fields: `confusedWith`, meaning hints, reading hints, failed source words, and generic shape guidance.
   - It intentionally leaves `sharedComponents` and `differingComponents` empty because the current bundle has no reliable component source.

## allowed_claim_types

Allowed now:

- Pair-source claim: “These kanji are in Kani’s visually similar source list.”
- Side-by-side prompt: “Compare `脱` with `説`.”
- Stroke-count claim when KANJIDIC2 and KanjiVG agree: “In the bundled data, `脱` has 11 strokes and `説` has 14.”
- Standalone dictionary clue: “`脱`: on-reading `ダツ`; kun-readings `ぬ.ぐ`, `ぬ.げる`; meanings include `undress`, `removing`, `escape from`, `take off`.”
- Source-card clue: “From your card: `脱力` (`だつりょく`) = loss of strength / exhaustion / weakness.”
- Radical-number clue only if labeled as dictionary metadata, e.g. “KANJIDIC2 radical number: `脱` = 130.” Do not present radical number as a visible component explanation.

Allowed only after more verified data/logic:

- “extra stroke” / “missing stroke”: requires deterministic KanjiVG stroke matching and tests for the pair.
- “left/right/top/bottom component differs”: requires licensed component/decomposition data or curated pair notes with provenance.
- “the only difference is ...”: requires high-confidence exact pair analysis.
- “longer/shorter stroke”: requires measured guide-shape comparison and copy such as “in the guide shape” to avoid overclaiming across fonts.

Never claim from current sources:

- WaniKani/Kiku reason text for similarity.
- Radical numbers as complete visible components.
- Prompt word meaning as standalone kanji meaning.
- Unsupported mnemonics or component names.

## fallback_policy

Primary CTA text must be exactly:

`Explore the differences`

Open the page/sheet after answer reveal/result. It may also be available from the similar-kanji card before grading, but it must not change scheduler state.

When a precise pair note is unavailable:

1. Show large side-by-side glyphs for the correct kanji and selected/watched distractor.
2. Show evidence labels:
   - `In similar-kanji source` when the pair exists in `similar_kanji.tsv`.
   - `No exact similar-pair source for this choice yet` when the selected wrong glyph is not a direct pair for the target.
3. Show stroke counts if both sources have data.
4. Show target/distractor readings and meanings from `kanji_dictionary.db` or current inventory.
5. Show source-card word evidence separately from standalone kanji dictionary evidence.
6. If no exact shape delta can be computed, say: `Kani can show the forms side by side, but it does not have a precise shape note for this pair yet.`
7. Do not mutate FSRS rating, ladder movement, review logs, or repair state from this page.

For the screenshot target `脱`:

- If the selected distractor is one of `悦`, `税`, `説`, `鋭`, use the direct-pair template.
- If the selected distractor is `称`, `吐`, or another non-direct row, use the no-exact-pair fallback. Do not say “not visually similar”; say only that Kani has no pair-source evidence for that exact target/distractor combination.

## copy_strings

Primary CTA:

- `Explore the differences`

Titles:

- `Explore the differences`
- `Why these kanji are easy to mix up`
- Japanese locale option: `違いを見比べる`

Direct-pair body:

- `Kani has {target} and {distractor} in its visually similar kanji source list.`
- `Look at the enlarged forms first, then use the dictionary clues below.`
- `Stroke counts: {target} has {targetStrokeCount}; {distractor} has {distractorStrokeCount}.`

Fallback body:

- `Kani can show these forms side by side, but it does not have a precise shape note for this pair yet.`
- `Use the enlarged glyphs, stroke counts, readings, and meanings as clues.`
- `This choice is not a direct pair for {target} in the current similar-kanji source.`

Source-word distinction:

- `From your card: {sourceWord} ({sourceReading}) = {sourceMeaning}.`
- `Standalone kanji clue: {target} = {dictionaryMeanings}.`

Screenshot-specific `脱` copy:

- `From your card: 脱力 (だつりょく) = loss of strength / exhaustion / weakness.`
- `Standalone kanji clue: 脱 = undress / removing / escape from / take off.`
- `Kani’s current similar-kanji source links 脱 with 悦, 税, 説, and 鋭.`

Empty-data copy:

- `No dictionary clue available for this kanji yet.`
- `No stroke guide available for this kanji yet.`
- `No exact similar-pair source for this choice yet.`

Action labels:

- `Back to question`
- `Show stroke guide`
- `Practice this pair`
- `I can tell them apart`

Accessibility/content descriptions:

- `Compare {target} with {distractor}`
- `{kanji}, {strokeCount} strokes, meanings: {meanings}`
- `No precise shape note available for {target} and {distractor}`

## sample_pairs

All rows were verified from the fresh `origin/main` scratch clone against `similar_kanji.tsv`, `kanji_dictionary.db`, and `kanji_strokes.tsv`.

1. `大` / `太`
   - Pair source: `similar_kanji.tsv:631` (`大	太	kiku:wk-visually-similar`).
   - Stroke counts: KANJIDIC2 `3/4`; KanjiVG `3/4`.
   - Dictionary: `大` = large/big; `太` = plump/thick/big around.
   - Safe copy: `太 has one more stroke than 大 in both bundled stroke sources. Compare the enlarged forms before choosing.`

2. `万` / `方`
   - Pair source: `similar_kanji.tsv:8` (`万	方	kiku:wk-visually-similar`).
   - Stroke counts: KANJIDIC2 `3/4`; KanjiVG `3/4`.
   - Dictionary: `万` = ten thousand/10,000; `方` = direction/person/alternative.
   - Safe copy: `方 has one more stroke than 万 in the bundled data. Kani does not yet have a precise component note for this pair.`

3. `末` / `未`
   - Pair source: `similar_kanji.tsv:1221` (`未	末	kiku:wk-visually-similar`).
   - Stroke counts: KANJIDIC2 `5/5`; KanjiVG `5/5`.
   - Dictionary: `末` = end/close/tip/posterity; `未` = not yet/still/sign of the ram.
   - Safe copy: `These have the same stroke count in the bundled data. Compare the enlarged forms; Kani does not yet have a precise shape note for this pair.`

4. `土` / `士`
   - Pair source: `similar_kanji.tsv:529` (`土	士	kiku:wk-visually-similar`).
   - Stroke counts: KANJIDIC2 `3/3`; KanjiVG `3/3`.
   - Dictionary: `土` = soil/earth/ground; `士` = gentleman/scholar/samurai.
   - Safe copy: `These have the same stroke count. Use side-by-side form comparison and dictionary clues; no precise shape note is bundled yet.`

5. `力` / `刀`
   - Pair source: `similar_kanji.tsv:286` (`刀	力	kiku:wk-visually-similar`).
   - Stroke counts: KANJIDIC2 `2/2`; KanjiVG `2/2`.
   - Dictionary: `力` = power/strength; `刀` = sword/saber/knife.
   - Safe copy: `These have the same stroke count but different dictionary meanings/readings. Compare the enlarged glyphs; no exact shape delta is bundled yet.`

6. `悦` / `脱` (screenshot target family)
   - Pair source: `similar_kanji.tsv:934` (`悦	脱	kiku:wk-visually-similar`).
   - Stroke counts: KANJIDIC2 `10/11`; KanjiVG `10/11`.
   - Dictionary: `悦` = ecstasy/joy/rapture; `脱` = undress/removing/escape from/get rid of/be left out/take off.
   - Safe copy: `脱 has 11 strokes; 悦 has 10. Kani has this pair in its visually similar source list. No precise component note is bundled yet.`

7. `税` / `脱` (screenshot target family)
   - Pair source: `similar_kanji.tsv:1595` (`税	脱	kiku:wk-visually-similar`).
   - Stroke counts: KANJIDIC2 `12/11`; KanjiVG `12/11`.
   - Dictionary: `税` = tax/duty; `脱` = undress/removing/escape from/get rid of/be left out/take off.
   - Safe copy: `税 has 12 strokes; 脱 has 11. Kani has this pair in its visually similar source list.`

8. `脱` / `説` (screenshot target family)
   - Pair source: `similar_kanji.tsv:1736` (`脱	説	kiku:wk-visually-similar`).
   - Stroke counts: KANJIDIC2 `11/14`; KanjiVG `11/14`.
   - Dictionary: `脱` = undress/removing/escape from/get rid of/be left out/take off; `説` = opinion/theory/explanation/rumor.
   - Safe copy: `説 has 14 strokes; 脱 has 11. Compare the enlarged forms and use the dictionary clues.`

9. `脱` / `鋭` (screenshot target family)
   - Pair source: `similar_kanji.tsv:1737` (`脱	鋭	kiku:wk-visually-similar`).
   - Stroke counts: KANJIDIC2 `11/15`; KanjiVG `11/15`.
   - Dictionary: `脱` = undress/removing/escape from/get rid of/be left out/take off; `鋭` = pointed/sharpness/edge/weapon/sharp/violent.
   - Safe copy: `鋭 has 15 strokes; 脱 has 11. Kani has this pair in its visually similar source list.`

10. `牛` / `午`
    - Pair source: `similar_kanji.tsv:350` (`午	牛	kiku:wk-visually-similar`).
    - Stroke counts: KANJIDIC2 `4/4`; KanjiVG `4/4`.
    - Dictionary: `牛` = cow; `午` = noon/sign of the horse/11AM-1PM.
    - Safe copy: `These have the same stroke count. Use side-by-side form comparison and dictionary clues; no exact shape delta is bundled yet.`

## risks

- Overclaiming components: current data has primary radical numbers and stroke paths, not component decomposition.
- Word-vs-kanji confusion: label card/source-word meaning separately from standalone dictionary meaning.
- Font/variant drift: KanjiVG guide shapes and Android fonts may differ; coordinate-derived claims should say “in the guide shape”.
- Licensing/provenance: KANJIDIC2 is CC BY-SA 4.0 via EDRDG; KanjiVG is CC BY-SA 3.0; similar pairs are transformed from Kiku/WaniKani page data. Any curated/component notes need their own provenance.
- Privacy: source words from imported Anki cards can be user data. Keep them local.
- UX safety: explanation pages are tutor/help copy only and must not change FSRS ratings, ladder movement, or review state.
- Missing exact session choices: screenshots alone may not recover all distractors. Prefer local session-state data for exact selected/visible choices.

## implementation handoff notes

- Add the CTA near the revealed answer/result area for `similar_kanji` first; consider reusing it for `meaning_kanji` later because the screenshot appears to be a meaning-choice route.
- Existing `SimilarKanjiExplanationPolicy` already provides a conservative fallback model; do not populate `sharedComponents` or `differingComponents` from guesses.
- If adding pair-specific notes later, store explicit records with fields such as `target`, `distractor`, `claim_type`, `copy`, `source_id`, `confidence`, and `reviewed_at`, rather than deriving copy ad hoc in UI code.
- Suggested initial test fixtures: `大/太` and `万/方` for verified stroke-count deltas, `末/未` and `土/士` for same-stroke fallback, and `脱/説` or `脱/鋭` for the screenshot target family.
