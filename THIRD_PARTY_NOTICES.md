# Third-party notices

Kani is licensed under the GNU Affero General Public License, version 3 or
later (`LICENSE`). This file records third-party code, models and data that
Kani contains or is derived from. Other data attributions live in
`docs/dictionary_sources.md`, `docs/similar_kanji_attribution.txt` and
`docs/fonts.md`.

## RWKV memory model (`rwkv/`)

The `:rwkv` module is a Kotlin port of the small (SM92) RWKV engine. It
covers the feature builder, the PyTorch MT19937 ID encodings, the RWKV-7
forward pass, the power-mixture curve head, the interval search and the int8
state compaction.

- **Source:** `Pedrubik2000/kuma3-anki-core`, branch `rwkv`, commit
  `a8cf880ce2907229d2edcadcd6977d855b7954a8`. The ported code comes mainly
  from `rslib/src/rwkv/engine.rs`, `architecture.rs` and `precision.rs`, with
  the history rules from `rslib/src/scheduler/rwkv/history.rs`. The parity
  harness mirrors `rslib/src/rwkv/sm40_tests.rs`.
- **Copyright:** Ankitects Pty Ltd and contributors, including the authors of
  the kuma3-anki and JSchoreels/anki forks.
- **License:** GNU AGPL, version 3 or later. Every ported file carries an
  `SPDX-License-Identifier: AGPL-3.0-or-later` header and names its upstream
  source.

The model architecture, training code and recurrent-inference design
originate in `open-spaced-repetition/srs-benchmark` (RWKV, by 1DWalker,
L-M-Sherlock and contributors). As of 2026-11-18 that repository has no
license file. Its issue #353, which asks for redistribution permission for
the code and trained checkpoint, is open.

`JSchoreels/anki-rwkv` packages the training and export tooling. Its own code
is MIT-licensed, and it states that the files derived from srs-benchmark are
not relicensed.

## RWKV SM92 weights (`app/src/main/assets/rwkv/sm92/weights.f32.bin`)

- **Model:** `sm92-e26-dec1ep32k`, 331,511 parameters, float32 (1,351,553
  bytes).
- **SHA-256:** `4fbc5f963b6ef18d23dbc0f11358fed8a9c7c97c271d6eda95df3b8ed6fbeb6e`
- **Obtained from:** `qt/aqt/rwkv_inference/sm92/weights.f32.bin` at the
  kuma3-anki-core commit above. The parity vectors in
  `rwkv/testdata/sm92-vectors.json.gz` come from the same commit.
- **Training:** by JSchoreels, on the `open-spaced-repetition/anki-revlogs-10k`
  dataset. That dataset is gated on Hugging Face and its license is listed
  as "other". Contributors in srs-benchmark issue #353 describe its terms as
  permitting use for memory research. They say that whether weights trained
  on it may be redistributed has not been settled.
- **Shipped in kuma3-anki** with the trainer's permission, according to that
  project's `RWKV.md`.

Kani bundles these weights on that basis. If the dataset's or trainer's terms
are clarified in a way that does not permit redistribution, the weights must
be removed from the APK.

## FSRS-6 (`fsrs-java/`)

The `fsrs-java` module is a port of `open-spaced-repetition/py-fsrs` v6.3.1
(see `FsrsAlgorithmInfo`). Kani no longer schedules with it. It remains a
test dependency of `:core`, where it is a closed-form memory-model test
double for scheduler policy tests.

## Google ML Kit Digital Ink Recognition

The handwriting recognizer is Google's ML Kit Digital Ink Recognition
library (`com.google.mlkit:digital-ink-recognition`). It is distributed
under Google's terms (the ML Kit Terms of Service), not under the AGPL.
