#!/usr/bin/env python3
"""Seed a small sanitized Kiku collection into the throwaway Anki fixture.

Goal 191 needs a repeatable live collection to qualify Kani's desktop provider
against a real Anki. The AnkiDroid CI fixture writes a `.anki2` file directly
(`create_ankidroid_kiku_fixture.py`), but that is not portable to Anki Desktop
26.05: its schema is far newer, is migrated by Anki's own Rust backend, and a
hand-written file pinned to one schema version becomes a maintenance trap the
first time upstream migrates. So this seeds through AnkiConnect instead and lets
Anki build its own collection.

Every kanji, reading, gloss, and sentence below is invented for this fixture.
There is no user content here, which is what makes the resulting evidence
publishable: the sanitized collection is the *only* collection the repeatable
suite reads.

## Safety

Writing is exactly what this script does, so it refuses to do it anywhere but
the fixture:

- The port must not be AnkiConnect's standard 8765, where the operator's own
  Anki listens.
- `getMediaDirPath` reports the *loaded* profile's media directory, and its path
  must contain the expected throwaway profile name. This is the check that
  distinguishes "an isolated fixture" from "someone's real collection that
  happens to be reachable"; `getProfiles` cannot answer it, because it lists
  every profile on the machine regardless of which is open.

Both are checked before the first write action, and either failing is a hard
exit rather than a warning.

Usage:
  seed_anki_desktop_kiku_collection.py [--endpoint URL] [--profile NAME]
                                       [--print-manifest]
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request

DEFAULT_ENDPOINT = "http://127.0.0.1:18765"
DEFAULT_PROFILE = "KaniFixture"

# The standard AnkiConnect port, where a real Anki is expected to be listening.
RESERVED_LIVE_PORT = 8765

MODEL_NAME = "Kiku"
TEMPLATE_NAME = "Mining"
DECK_NAME = "KaniFixtureMining"

FIELDS = [
    "Expression",
    "ExpressionReading",
    "MainDefinition",
    "Sentence",
    "Frequency",
    "FreqSort",
]

# The browser-query tag, exercising the merge path where a note is selected by
# the user's own Anki search rather than by the configured model query.
BROWSER_TAG = "kani_query_test"

# Card states the suite needs to observe end to end. `queue` values follow
# Anki's own encoding: 2 review, 1 learning, 0 new, -1 suspended, -2 buried.
#
# `answer_ease` means "let Anki's own scheduler produce this state by answering
# the card", rather than writing the columns. That is the only way to get a
# genuine learning card, and it turned up a limit worth recording: AnkiConnect
# refuses to write a negative `ivl` at all ("Value out of range: -600", and
# likewise for -1), so the legacy negative-seconds interval encoding cannot be
# produced through this fixture. On Anki 26.05's v3 scheduler an answered
# learning card stores `ivl = 1` with the sub-day step tracked in `due`/`left`,
# so the negative encoding does not arise here either.
#
# The interval-flooring rule in `ProviderCardPolicy` is therefore *not* dead
# code and is *not* covered by this live fixture: it defends the AnkiDroid path
# and older collections, and it is pinned by
# `AnkiDroidCrossProviderConformanceInstrumentedTest` over a synthetic cursor.
# Do not "simplify" it away on the strength of this fixture alone.
NOTES = [
    {
        "kanji": "箱",
        "reading": "はこ",
        "meaning": "box",
        "sentence": "箱を開けた。",
        "frequency": 100,
        "tags": ["kiku_ci"],
        "card": {"queue": 2, "type": 2, "ivl": 42, "reps": 80, "lapses": 3},
    },
    {
        "kanji": "橋",
        "reading": "はし",
        "meaning": "bridge",
        "sentence": "橋を渡る。",
        "frequency": 200,
        "tags": ["kiku_ci", BROWSER_TAG],
        "card": {"queue": 2, "type": 2, "ivl": 7, "reps": 9, "lapses": 3},
    },
    {
        "kanji": "箸",
        "reading": "はし",
        "meaning": "chopsticks",
        "sentence": "箸で食べる。",
        "frequency": 300,
        "tags": ["kiku_ci"],
        "card": {"queue": 2, "type": 2, "ivl": 28, "reps": 18, "lapses": 3},
    },
    {
        "kanji": "端",
        "reading": "はし",
        "meaning": "edge",
        "sentence": "端に寄せる。",
        "frequency": 400,
        "tags": ["kiku_ci"],
        "card": {"queue": 2, "type": 2, "ivl": 35, "reps": 24, "lapses": 3},
    },
    {
        # Suspended and mature: the archive-tag and evidence-seeding paths.
        "kanji": "鍵",
        "reading": "かぎ",
        "meaning": "key",
        "sentence": "鍵を掛ける。",
        "frequency": 500,
        "tags": ["kiku_ci"],
        "card": {"queue": -1, "type": 2, "ivl": 90, "reps": 40, "lapses": 1},
    },
    {
        # A real learning card, produced by answering rather than by writing
        # columns, so its shape is whatever Anki's scheduler actually produces.
        "kanji": "傘",
        "reading": "かさ",
        "meaning": "umbrella",
        "sentence": "傘を持つ。",
        "frequency": 600,
        "tags": ["kiku_ci"],
        "card": {"answer_ease": 3},
    },
    {
        # Brand new: no review history at all.
        "kanji": "靴",
        "reading": "くつ",
        "meaning": "shoes",
        "sentence": "靴を履く。",
        "frequency": 700,
        "tags": ["kiku_ci"],
        "card": {"queue": 0, "type": 0, "ivl": 0, "reps": 0, "lapses": 0},
    },
    {
        # Buried, not suspended. Kani's `queue < 0` rule must catch this too;
        # AnkiConnect's own `areSuspended` would not.
        "kanji": "窓",
        "reading": "まど",
        "meaning": "window",
        "sentence": "窓を閉める。",
        "frequency": 800,
        "tags": ["kiku_ci"],
        "card": {"queue": -2, "type": 2, "ivl": 21, "reps": 12, "lapses": 2},
    },
]


class AnkiConnectError(RuntimeError):
    """An AnkiConnect action returned an error, or the transport failed."""


def invoke(endpoint: str, action: str, **params: object) -> object:
    payload = {"action": action, "version": 6}
    if params:
        payload["params"] = params
    request = urllib.request.Request(
        endpoint,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json; charset=utf-8"},
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            body = json.loads(response.read().decode("utf-8"))
    except (urllib.error.URLError, OSError, json.JSONDecodeError) as failure:
        raise AnkiConnectError(f"{action}: transport failed: {failure}") from failure
    # AnkiConnect reports action failures in the body with HTTP 200, so the
    # status code alone is not a success signal.
    if body.get("error") is not None:
        raise AnkiConnectError(f"{action}: {body['error']}")
    return body.get("result")


def assert_isolated_fixture(endpoint: str, profile: str) -> None:
    """Refuses to write anywhere but the throwaway fixture profile."""
    if f":{RESERVED_LIVE_PORT}" in endpoint:
        raise SystemExit(
            f"Refusing to seed {endpoint}: port {RESERVED_LIVE_PORT} is where a "
            "real Anki listens. Use the fixture's isolated port.",
        )
    media_dir = invoke(endpoint, "getMediaDirPath")
    if not isinstance(media_dir, str) or profile not in media_dir:
        raise SystemExit(
            f"Refusing to seed {endpoint}: the loaded profile's media directory "
            f"does not name the expected throwaway profile {profile!r}. "
            "This may be a real collection.",
        )


def ensure_model(endpoint: str) -> None:
    if MODEL_NAME in invoke(endpoint, "modelNames"):
        return
    invoke(
        endpoint,
        "createModel",
        modelName=MODEL_NAME,
        inOrderFields=FIELDS,
        cardTemplates=[
            {
                "Name": TEMPLATE_NAME,
                "Front": "{{Expression}}",
                "Back": "{{ExpressionReading}}<br>{{MainDefinition}}<br>{{Sentence}}",
            },
        ],
    )


def seed(endpoint: str, profile: str) -> dict[str, object]:
    assert_isolated_fixture(endpoint, profile)
    ensure_model(endpoint)
    invoke(endpoint, "createDeck", deck=DECK_NAME)

    added, skipped = [], 0
    for note in NOTES:
        note_ids = invoke(
            endpoint,
            "findNotes",
            query=f'"note:{MODEL_NAME}" "Expression:{note["kanji"]}"',
        )
        if note_ids:
            # Converge rather than skip. The note already exists, but its card
            # state may not match — an interrupted or older run can leave the
            # note added and the scheduling write unapplied, and a fixture that
            # silently keeps Anki's defaults is worse than one that fails. The
            # scheduling write is idempotent, so re-applying is safe.
            skipped += 1
            apply_card_state(endpoint, note_ids[0], note["card"])
            continue
        result = invoke(
            endpoint,
            "addNotes",
            notes=[
                {
                    "deckName": DECK_NAME,
                    "modelName": MODEL_NAME,
                    "fields": {
                        "Expression": note["kanji"],
                        "ExpressionReading": note["reading"],
                        "MainDefinition": note["meaning"],
                        "Sentence": note["sentence"],
                        "Frequency": str(note["frequency"]),
                        "FreqSort": str(note["frequency"]),
                    },
                    "tags": note["tags"],
                },
            ],
        )
        note_id = result[0]
        if note_id is None:
            raise AnkiConnectError(f"addNotes refused the note for {note['kanji']}")
        added.append(note_id)
        apply_card_state(endpoint, note_id, note["card"])

    return {
        "model": MODEL_NAME,
        "deck": DECK_NAME,
        "notes_added": len(added),
        "notes_already_present": skipped,
        "notes_total": len(NOTES),
    }


def apply_card_state(endpoint: str, note_id: int, card: dict[str, object]) -> None:
    """Writes the scheduling state the suite needs to observe.

    `setSpecificValueOfCard` needs its warning acknowledged, because Anki does
    not intend arbitrary scheduling writes. That is acceptable *here* and only
    here: this is a throwaway fixture whose whole purpose is to present known
    card states. Kani's own provider never writes scheduling state.

    Two things about this action only showed up against the real host, and both
    would have made the fixture quietly wrong:

    - **Values must be JSON numbers, not strings.** Strings are rejected with
      "'str' object cannot be interpreted as an integer".
    - **Failures are reported per item inside `result`, with the envelope's
      `error` still null.** So the generic success check in `invoke` passes while
      nothing was written. The first version of this script sent strings and
      reported eight seeded notes with every card left at Anki's defaults.
    """
    card_ids = invoke(endpoint, "findCards", query=f"nid:{note_id}")
    if not card_ids:
        raise AnkiConnectError(f"note {note_id} produced no cards")
    card_id = card_ids[0]

    if "answer_ease" in card:
        # Let Anki's scheduler decide the state. Answering an already-answered
        # card again would keep advancing it, so this is skipped once the card
        # has left the new queue -- which keeps the seeder idempotent.
        info = invoke(endpoint, "cardsInfo", cards=[card_id])
        if info and info[0].get("type") == 0:
            answered = invoke(
                endpoint,
                "answerCards",
                answers=[{"cardId": card_id, "ease": int(card["answer_ease"])}],
            )
            if answered != [True]:
                raise AnkiConnectError(f"answerCards refused card {card_id}: {answered}")
        return

    result = invoke(
        endpoint,
        "setSpecificValueOfCard",
        card=card_id,
        keys=["type", "queue", "ivl", "reps", "lapses"],
        newValues=[
            int(card["type"]),
            int(card["queue"]),
            int(card["ivl"]),
            int(card["reps"]),
            int(card["lapses"]),
        ],
        warning_check=True,
    )
    # Each entry is `true`, or `[false, reason]`. Anything not `true` is a
    # silent no-op unless it is raised here.
    if not isinstance(result, list) or not all(entry is True for entry in result):
        raise AnkiConnectError(
            f"setSpecificValueOfCard did not apply to card {card_id}: {result}",
        )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--endpoint", default=DEFAULT_ENDPOINT)
    parser.add_argument("--profile", default=DEFAULT_PROFILE)
    parser.add_argument(
        "--print-manifest",
        action="store_true",
        help="print the sanitized collection manifest as JSON and exit",
    )
    args = parser.parse_args()

    if args.print_manifest:
        print(json.dumps(manifest(), ensure_ascii=False, indent=2, sort_keys=True))
        return 0

    try:
        summary = seed(args.endpoint, args.profile)
    except AnkiConnectError as failure:
        print(f"Seeding failed: {failure}", file=sys.stderr)
        return 1
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 0


def manifest() -> dict[str, object]:
    """The fixture's shape, for the runbook and for the Kotlin suite to assert."""
    return {
        "model": MODEL_NAME,
        "template": TEMPLATE_NAME,
        "deck": DECK_NAME,
        "fields": FIELDS,
        "browser_tag": BROWSER_TAG,
        "notes": len(NOTES),
        "suspended_notes": sum(1 for n in NOTES if n["card"].get("queue") == -1),
        "buried_notes": sum(1 for n in NOTES if n["card"].get("queue") == -2),
        "learning_notes": sum(1 for n in NOTES if "answer_ease" in n["card"]),
        "new_notes": sum(1 for n in NOTES if n["card"].get("queue") == 0),
        "browser_tagged_notes": sum(1 for n in NOTES if BROWSER_TAG in n["tags"]),
    }


if __name__ == "__main__":
    sys.exit(main())
