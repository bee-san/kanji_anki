from __future__ import annotations

from dataclasses import dataclass

API_BASE_URL = "https://api.jiten.moe/api/"
DEFAULT_JITEN_FREQUENCY_LIST_ID = "global"
LEGACY_DEFAULT_VN_CSV_URL = (
    "https://api.jiten.moe/api/frequency-list/download"
    "?downloadType=csv&mediaType=7"
)


@dataclass(frozen=True)
class JitenFrequencyList:
    id: str
    label: str
    csv_path: str
    bundled_snapshot_name: str | None = None
    is_media_specific: bool = False

    @property
    def csv_url(self) -> str:
        return API_BASE_URL + self.csv_path

    @property
    def dropdown_label(self) -> str:
        if self.is_media_specific:
            return f"{self.label} (media)"
        return self.label


JITEN_FREQUENCY_LISTS = (
    JitenFrequencyList(
        id="global",
        label="Global",
        csv_path="frequency-list/download?downloadType=csv",
        bundled_snapshot_name="jiten_frequency_global.csv",
    ),
    JitenFrequencyList(
        id="kanji",
        label="Kanji",
        csv_path="frequency-list/download-kanji?downloadType=csv",
    ),
    JitenFrequencyList(
        id="anime",
        label="Anime",
        csv_path="frequency-list/download?downloadType=csv&mediaType=1",
        is_media_specific=True,
    ),
    JitenFrequencyList(
        id="audio",
        label="Audio",
        csv_path="frequency-list/download?downloadType=csv&mediaType=10",
        is_media_specific=True,
    ),
    JitenFrequencyList(
        id="drama",
        label="Drama",
        csv_path="frequency-list/download?downloadType=csv&mediaType=2",
        is_media_specific=True,
    ),
    JitenFrequencyList(
        id="manga",
        label="Manga",
        csv_path="frequency-list/download?downloadType=csv&mediaType=9",
        is_media_specific=True,
    ),
    JitenFrequencyList(
        id="movie",
        label="Movie",
        csv_path="frequency-list/download?downloadType=csv&mediaType=3",
        is_media_specific=True,
    ),
    JitenFrequencyList(
        id="non_fiction",
        label="Non-Fiction",
        csv_path="frequency-list/download?downloadType=csv&mediaType=5",
        is_media_specific=True,
    ),
    JitenFrequencyList(
        id="novel",
        label="Novel",
        csv_path="frequency-list/download?downloadType=csv&mediaType=4",
        is_media_specific=True,
    ),
    JitenFrequencyList(
        id="video_game",
        label="Video Game",
        csv_path="frequency-list/download?downloadType=csv&mediaType=6",
        is_media_specific=True,
    ),
    JitenFrequencyList(
        id="visual_novel",
        label="Visual Novel",
        csv_path="frequency-list/download?downloadType=csv&mediaType=7",
        is_media_specific=True,
    ),
    JitenFrequencyList(
        id="web_novel",
        label="Web Novel",
        csv_path="frequency-list/download?downloadType=csv&mediaType=8",
        is_media_specific=True,
    ),
)

_LISTS_BY_ID = {definition.id: definition for definition in JITEN_FREQUENCY_LISTS}
_LIST_IDS = tuple(definition.id for definition in JITEN_FREQUENCY_LISTS)


def frequency_list_ids() -> tuple[str, ...]:
    return _LIST_IDS


def get_frequency_list_definition(list_id: str) -> JitenFrequencyList:
    return _LISTS_BY_ID.get(list_id, _LISTS_BY_ID[DEFAULT_JITEN_FREQUENCY_LIST_ID])


def dropdown_options() -> tuple[tuple[str, str], ...]:
    return tuple(
        (definition.id, definition.dropdown_label)
        for definition in JITEN_FREQUENCY_LISTS
    )
