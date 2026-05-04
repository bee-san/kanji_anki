from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any, Iterable, Mapping
from urllib.error import URLError
from urllib.request import Request, urlopen

from .config import AppSettings

CHUNK_SIZE = 500


class AnkiConnectError(RuntimeError):
    pass


@dataclass(frozen=True)
class NoteSnapshot:
    note_id: int
    model_name: str
    expression: str
    reading: str
    meaning: str
    fields: dict[str, str]
    tags: tuple[str, ...]
    card_ids: tuple[int, ...]


@dataclass(frozen=True)
class CardSnapshot:
    card_id: int
    note_id: int
    deck_name: str
    interval_days: int
    due: int
    card_ord: int
    queue: int
    card_type: int
    reps: int
    lapses: int
    modified_ts: int = 0

    @property
    def is_suspended(self) -> bool:
        return self.queue == -1

    @property
    def is_active(self) -> bool:
        return not self.is_suspended


@dataclass(frozen=True)
class CollectionSnapshot:
    notes: tuple[NoteSnapshot, ...]
    cards: tuple[CardSnapshot, ...]


class AnkiConnectClient:
    def __init__(self, base_url: str, *, timeout_seconds: int = 20) -> None:
        self._base_url = base_url.rstrip("/")
        self._timeout_seconds = timeout_seconds

    def sync_snapshot(self, settings: AppSettings) -> CollectionSnapshot:
        note_ids = self._find_note_ids(settings.model_names)
        notes = self._load_notes(note_ids, settings)
        cards = self._load_cards(
            sorted({card_id for note in notes for card_id in note.card_ids})
        )
        return CollectionSnapshot(notes=notes, cards=cards)

    def _find_note_ids(self, model_names: tuple[str, ...]) -> list[int]:
        note_ids: set[int] = set()
        for model_name in model_names:
            query = f'note:"{model_name}"'
            result = self._invoke("findNotes", query=query)
            for note_id in result or []:
                try:
                    note_ids.add(int(note_id))
                except (TypeError, ValueError):
                    continue
        return sorted(note_ids)

    def _load_notes(
        self,
        note_ids: Iterable[int],
        settings: AppSettings,
    ) -> tuple[NoteSnapshot, ...]:
        notes: list[NoteSnapshot] = []
        for chunk in _chunked(note_ids, CHUNK_SIZE):
            for payload in self._invoke("notesInfo", notes=list(chunk)) or []:
                if not isinstance(payload, Mapping):
                    continue
                fields_payload = payload.get("fields") or {}
                fields = {
                    str(key): str((value or {}).get("value", ""))
                    for key, value in fields_payload.items()
                    if isinstance(key, str) and isinstance(value, Mapping)
                }
                notes.append(
                    NoteSnapshot(
                        note_id=int(payload.get("noteId") or payload.get("note") or 0),
                        model_name=str(payload.get("modelName") or ""),
                        expression=fields.get(settings.expression_field, ""),
                        reading=fields.get(settings.reading_field, ""),
                        meaning=fields.get(settings.meaning_field, ""),
                        fields=fields,
                        tags=tuple(str(tag) for tag in payload.get("tags") or [] if tag),
                        card_ids=tuple(
                            int(card_id)
                            for card_id in payload.get("cards") or []
                            if _is_int_like(card_id)
                        ),
                    )
                )
        return tuple(note for note in notes if note.note_id > 0)

    def _load_cards(self, card_ids: Iterable[int]) -> tuple[CardSnapshot, ...]:
        cards: list[CardSnapshot] = []
        for chunk in _chunked(card_ids, CHUNK_SIZE):
            for payload in self._invoke("cardsInfo", cards=list(chunk)) or []:
                if not isinstance(payload, Mapping):
                    continue
                cards.append(
                    CardSnapshot(
                        card_id=int(payload.get("cardId") or 0),
                        note_id=int(payload.get("note") or 0),
                        deck_name=str(payload.get("deckName") or ""),
                        interval_days=int(payload.get("interval") or payload.get("ivl") or 0),
                        due=int(payload.get("due") or 0),
                        card_ord=int(payload.get("ord") or 0),
                        queue=int(payload.get("queue") or 0),
                        card_type=int(payload.get("type") or 0),
                        reps=int(payload.get("reps") or 0),
                        lapses=int(payload.get("lapses") or 0),
                        modified_ts=int(payload.get("mod") or 0),
                    )
                )
        return tuple(card for card in cards if card.card_id > 0 and card.note_id > 0)

    def _invoke(self, action: str, **params: Any) -> Any:
        payload = json.dumps(
            {
                "action": action,
                "version": 6,
                "params": params,
            }
        ).encode("utf-8")
        request = Request(
            self._base_url,
            data=payload,
            headers={
                "Content-Type": "application/json",
                "Accept": "application/json",
            },
            method="POST",
        )
        try:
            with urlopen(request, timeout=self._timeout_seconds) as response:  # noqa: S310
                raw = response.read()
        except URLError as error:  # pragma: no cover - network path
            raise AnkiConnectError(f"Could not reach AnkiConnect at {self._base_url}: {error}") from error

        try:
            decoded = json.loads(raw.decode("utf-8"))
        except ValueError as error:
            raise AnkiConnectError(
                f"AnkiConnect returned invalid JSON for action {action}."
            ) from error

        if not isinstance(decoded, Mapping):
            raise AnkiConnectError(f"AnkiConnect returned an invalid payload for action {action}.")
        if decoded.get("error"):
            raise AnkiConnectError(str(decoded["error"]))
        return decoded.get("result")


def _chunked(values: Iterable[int], size: int) -> Iterable[list[int]]:
    bucket: list[int] = []
    for value in values:
        bucket.append(int(value))
        if len(bucket) >= size:
            yield bucket
            bucket = []
    if bucket:
        yield bucket


def _is_int_like(value: Any) -> bool:
    try:
        int(value)
    except (TypeError, ValueError):
        return False
    return True
