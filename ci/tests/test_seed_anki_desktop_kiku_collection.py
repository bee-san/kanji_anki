"""Deterministic guards for the sanitized Kiku fixture seeder.

Seeding needs a running Anki, so the seeding itself cannot run here. What these
tests protect is what would be dangerous or silently wrong if it regressed: the
refusal to write anywhere but the throwaway fixture profile, the per-item failure
check that AnkiConnect hides inside a successful envelope, and the card states
the live qualification suite asserts against by count.
"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path
from typing import Any
from unittest import mock

REPO_ROOT = Path(__file__).resolve().parents[2]
SCRIPT = REPO_ROOT / "ci" / "scripts" / "seed_anki_desktop_kiku_collection.py"

sys.path.insert(0, str(REPO_ROOT / "ci" / "scripts"))
import seed_anki_desktop_kiku_collection as seeder  # noqa: E402


class IsolationTest(unittest.TestCase):
    """Two independent guards, either of which failing is a hard exit."""

    def test_refuses_the_live_ankiconnect_port(self) -> None:
        # 8765 is where the operator's own Anki listens, and this script writes.
        with self.assertRaises(SystemExit) as raised:
            seeder.assert_isolated_fixture("http://127.0.0.1:8765", "KaniFixture")

        self.assertIn("8765", str(raised.exception))

    def test_refuses_a_profile_that_is_not_the_throwaway_fixture(self) -> None:
        # `getMediaDirPath` reports the *loaded* profile, which is the only way to
        # tell an isolated fixture from a real collection that happens to answer
        # on the fixture's port. `getProfiles` lists every profile regardless of
        # which one is open, so it cannot answer this.
        with mock.patch.object(
            seeder, "invoke", return_value="/home/someone/.local/share/Anki2/User 1/collection.media"
        ):
            with self.assertRaises(SystemExit) as raised:
                seeder.assert_isolated_fixture("http://127.0.0.1:18765", "KaniFixture")

        self.assertIn("KaniFixture", str(raised.exception))

    def test_refuses_a_media_path_that_is_not_a_string(self) -> None:
        with mock.patch.object(seeder, "invoke", return_value=None):
            with self.assertRaises(SystemExit):
                seeder.assert_isolated_fixture("http://127.0.0.1:18765", "KaniFixture")

    def test_accepts_the_fixture_profile(self) -> None:
        with mock.patch.object(
            seeder, "invoke", return_value="/tmp/kani-fixture/base/KaniFixture/collection.media"
        ):
            seeder.assert_isolated_fixture("http://127.0.0.1:18765", "KaniFixture")

    def test_the_default_endpoint_is_the_isolated_port(self) -> None:
        self.assertEqual("http://127.0.0.1:18765", seeder.DEFAULT_ENDPOINT)
        self.assertEqual("KaniFixture", seeder.DEFAULT_PROFILE)
        # The port guard must be colon-anchored, because "18765" contains "8765":
        # an unanchored substring check would reject the fixture's own port.
        self.assertNotIn(f":{seeder.RESERVED_LIVE_PORT}", seeder.DEFAULT_ENDPOINT)


class FailureDetectionTest(unittest.TestCase):
    """AnkiConnect reports some failures where a naive check cannot see them."""

    def test_an_action_error_raises_even_though_the_transport_succeeded(self) -> None:
        # Action failures come back with HTTP 200 and a non-null `error`, so the
        # status code alone is not a success signal.
        with mock.patch.object(seeder.urllib.request, "urlopen") as urlopen:
            urlopen.return_value.__enter__.return_value.read.return_value = (
                b'{"result": null, "error": "deck was not found"}'
            )
            with self.assertRaises(seeder.AnkiConnectError) as raised:
                seeder.invoke("http://127.0.0.1:18765", "createDeck", deck="x")

        self.assertIn("deck was not found", str(raised.exception))

    def test_a_transport_failure_raises_an_ankiconnect_error(self) -> None:
        with mock.patch.object(seeder.urllib.request, "urlopen", side_effect=OSError("refused")):
            with self.assertRaises(seeder.AnkiConnectError):
                seeder.invoke("http://127.0.0.1:18765", "version")

    def test_a_per_item_scheduling_refusal_is_not_treated_as_success(self) -> None:
        # `setSpecificValueOfCard` reports per-item failures *inside* `result`
        # with the envelope's `error` still null. The first version of the seeder
        # missed this and reported eight seeded notes with every card left at
        # Anki's defaults.
        calls: list[str] = []

        def fake_invoke(_endpoint: str, action: str, **_params: Any) -> Any:
            calls.append(action)
            if action == "findCards":
                return [55]
            return [[False, "'str' object cannot be interpreted as an integer"]]

        with mock.patch.object(seeder, "invoke", fake_invoke):
            with self.assertRaises(seeder.AnkiConnectError) as raised:
                seeder.apply_card_state(
                    "http://127.0.0.1:18765",
                    7,
                    {"queue": 2, "type": 2, "ivl": 42, "reps": 80, "lapses": 3},
                )

        self.assertIn("did not apply", str(raised.exception))
        self.assertIn("setSpecificValueOfCard", calls)

    def test_a_note_producing_no_cards_raises(self) -> None:
        with mock.patch.object(seeder, "invoke", return_value=[]):
            with self.assertRaises(seeder.AnkiConnectError):
                seeder.apply_card_state("http://127.0.0.1:18765", 7, {"queue": 0, "type": 0, "ivl": 0, "reps": 0, "lapses": 0})


class SchedulingWriteTest(unittest.TestCase):
    """The shape of the scheduling write, which only a real host rejected."""

    def test_values_are_sent_as_json_numbers_with_the_warning_acknowledged(self) -> None:
        sent: dict[str, Any] = {}

        def fake_invoke(_endpoint: str, action: str, **params: Any) -> Any:
            if action == "findCards":
                return [55]
            sent.update(params)
            return [True]

        with mock.patch.object(seeder, "invoke", fake_invoke):
            seeder.apply_card_state(
                "http://127.0.0.1:18765",
                7,
                {"queue": -2, "type": 2, "ivl": 21, "reps": 12, "lapses": 2},
            )

        # Strings are rejected by the real host with "'str' object cannot be
        # interpreted as an integer", and the write needs its warning
        # acknowledged because Anki does not intend arbitrary scheduling writes.
        self.assertEqual([2, -2, 21, 12, 2], sent["newValues"])
        self.assertTrue(all(isinstance(value, int) for value in sent["newValues"]))
        self.assertEqual(["type", "queue", "ivl", "reps", "lapses"], sent["keys"])
        self.assertTrue(sent["warning_check"])

    def test_an_answered_card_is_left_alone_so_the_seeder_is_idempotent(self) -> None:
        # Answering an already-answered card again would keep advancing it, so a
        # re-run must not touch a card that has left the new queue.
        actions: list[str] = []

        def fake_invoke(_endpoint: str, action: str, **_params: Any) -> Any:
            actions.append(action)
            if action == "findCards":
                return [55]
            if action == "cardsInfo":
                return [{"type": 1}]
            raise AssertionError(f"unexpected {action}")

        with mock.patch.object(seeder, "invoke", fake_invoke):
            seeder.apply_card_state("http://127.0.0.1:18765", 7, {"answer_ease": 3})

        self.assertNotIn("answerCards", actions)

    def test_a_new_card_is_answered_to_produce_a_real_learning_state(self) -> None:
        # Writing the columns cannot produce a genuine learning card; only
        # letting Anki's own scheduler answer it can.
        answers: list[Any] = []

        def fake_invoke(_endpoint: str, action: str, **params: Any) -> Any:
            if action == "findCards":
                return [55]
            if action == "cardsInfo":
                return [{"type": 0}]
            if action == "answerCards":
                answers.append(params["answers"])
                return [True]
            raise AssertionError(f"unexpected {action}")

        with mock.patch.object(seeder, "invoke", fake_invoke):
            seeder.apply_card_state("http://127.0.0.1:18765", 7, {"answer_ease": 3})

        self.assertEqual([[{"cardId": 55, "ease": 3}]], answers)

    def test_a_refused_answer_raises(self) -> None:
        def fake_invoke(_endpoint: str, action: str, **_params: Any) -> Any:
            if action == "findCards":
                return [55]
            if action == "cardsInfo":
                return [{"type": 0}]
            return [False]

        with mock.patch.object(seeder, "invoke", fake_invoke):
            with self.assertRaises(seeder.AnkiConnectError):
                seeder.apply_card_state("http://127.0.0.1:18765", 7, {"answer_ease": 3})


class ManifestTest(unittest.TestCase):
    """The counts the Kotlin qualification suite asserts against."""

    def test_the_manifest_matches_what_the_live_suite_expects(self) -> None:
        # These are the same numbers as `LiveAnkiDesktopQualificationTest`'s
        # FIXTURE_* constants. If the fixture's shape changes, both move together
        # or the live suite fails against a fixture it no longer describes.
        self.assertEqual(
            {
                "model": "Kiku",
                "template": "Mining",
                "deck": "KaniFixtureMining",
                "fields": [
                    "Expression",
                    "ExpressionReading",
                    "MainDefinition",
                    "Sentence",
                    "Frequency",
                    "FreqSort",
                ],
                "browser_tag": "kani_query_test",
                "notes": 8,
                "suspended_notes": 1,
                "buried_notes": 1,
                "learning_notes": 1,
                "new_notes": 1,
                "browser_tagged_notes": 1,
            },
            seeder.manifest(),
        )

    def test_the_fixture_covers_both_kinds_of_negative_queue(self) -> None:
        # Kani treats every `queue < 0` as suspended, which is deliberately wider
        # than AnkiConnect's own `areSuspended` (`queue == -1`). A fixture with
        # only -1 would not exercise the difference.
        queues = {note["card"].get("queue") for note in seeder.NOTES}

        self.assertIn(-1, queues)
        self.assertIn(-2, queues)

    def test_every_note_is_invented_fixture_content(self) -> None:
        # The evidence in the runbook is publishable only because no user content
        # reaches this collection. Every note carries the CI tag that marks it.
        for note in seeder.NOTES:
            self.assertIn("kiku_ci", note["tags"], note["kanji"])
        self.assertEqual(len(seeder.NOTES), len({note["kanji"] for note in seeder.NOTES}))

    def test_no_note_requests_a_negative_interval(self) -> None:
        # AnkiConnect refuses to write a negative `ivl` ("Value out of range"),
        # so the legacy negative-seconds encoding cannot be produced here. The
        # interval-flooring rule in `ProviderCardPolicy` is therefore not covered
        # by this fixture and must not be dropped on its strength.
        for note in seeder.NOTES:
            self.assertGreaterEqual(note["card"].get("ivl", 0), 0, note["kanji"])

    def test_print_manifest_exits_cleanly_without_touching_a_host(self) -> None:
        import json
        import subprocess

        result = subprocess.run(
            [sys.executable, str(SCRIPT), "--print-manifest"],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=30,
            check=False,
        )

        self.assertEqual(0, result.returncode, result.stdout)
        self.assertEqual(seeder.manifest(), json.loads(result.stdout))

    def test_a_seeding_failure_is_reported_as_a_nonzero_exit(self) -> None:
        with mock.patch.object(seeder, "seed", side_effect=seeder.AnkiConnectError("nope")):
            with mock.patch.object(sys, "argv", ["seed", "--endpoint", "http://127.0.0.1:18765"]):
                self.assertEqual(1, seeder.main())


class SeedFlowTest(unittest.TestCase):
    """The seed run itself, with the host faked out."""

    def _fake_host(self) -> tuple[list[tuple[str, dict[str, Any]]], Any]:
        calls: list[tuple[str, dict[str, Any]]] = []

        def fake_invoke(_endpoint: str, action: str, **params: Any) -> Any:
            calls.append((action, params))
            if action == "getMediaDirPath":
                return "/tmp/base/KaniFixture/collection.media"
            if action == "modelNames":
                return []
            if action == "findNotes":
                return []
            if action == "addNotes":
                return [1000 + len([c for c in calls if c[0] == "addNotes"])]
            if action == "findCards":
                return [55]
            if action == "cardsInfo":
                return [{"type": 0}]
            if action == "answerCards":
                return [True]
            if action == "setSpecificValueOfCard":
                return [True]
            return None

        return calls, fake_invoke

    def test_a_fresh_seed_creates_the_model_deck_and_every_note(self) -> None:
        calls, fake_invoke = self._fake_host()

        with mock.patch.object(seeder, "invoke", fake_invoke):
            summary = seeder.seed("http://127.0.0.1:18765", "KaniFixture")

        self.assertEqual(len(seeder.NOTES), summary["notes_added"])
        self.assertEqual(0, summary["notes_already_present"])
        actions = [action for action, _ in calls]
        # Isolation is proved before the first write, not after it.
        self.assertEqual("getMediaDirPath", actions[0])
        self.assertLess(actions.index("getMediaDirPath"), actions.index("createModel"))
        self.assertIn("createDeck", actions)

    def test_an_existing_model_is_reused_rather_than_recreated(self) -> None:
        calls, fake_invoke = self._fake_host()

        def with_model(endpoint: str, action: str, **params: Any) -> Any:
            if action == "modelNames":
                calls.append((action, params))
                return [seeder.MODEL_NAME]
            return fake_invoke(endpoint, action, **params)

        with mock.patch.object(seeder, "invoke", with_model):
            seeder.seed("http://127.0.0.1:18765", "KaniFixture")

        self.assertNotIn("createModel", [action for action, _ in calls])

    def test_a_re_run_converges_existing_notes_instead_of_skipping_them(self) -> None:
        # An interrupted earlier run can leave a note added and its scheduling
        # write unapplied. A fixture that silently keeps Anki's defaults is worse
        # than one that fails, so the idempotent scheduling write is re-applied.
        calls, fake_invoke = self._fake_host()

        def with_notes(endpoint: str, action: str, **params: Any) -> Any:
            if action == "findNotes":
                calls.append((action, params))
                return [77]
            return fake_invoke(endpoint, action, **params)

        with mock.patch.object(seeder, "invoke", with_notes):
            summary = seeder.seed("http://127.0.0.1:18765", "KaniFixture")

        self.assertEqual(0, summary["notes_added"])
        self.assertEqual(len(seeder.NOTES), summary["notes_already_present"])
        actions = [action for action, _ in calls]
        self.assertNotIn("addNotes", actions)
        self.assertIn("setSpecificValueOfCard", actions)

    def test_a_refused_note_raises_rather_than_seeding_a_partial_fixture(self) -> None:
        _calls, fake_invoke = self._fake_host()

        def refusing(endpoint: str, action: str, **params: Any) -> Any:
            if action == "addNotes":
                return [None]
            return fake_invoke(endpoint, action, **params)

        with mock.patch.object(seeder, "invoke", refusing):
            with self.assertRaises(seeder.AnkiConnectError):
                seeder.seed("http://127.0.0.1:18765", "KaniFixture")


if __name__ == "__main__":
    unittest.main()
