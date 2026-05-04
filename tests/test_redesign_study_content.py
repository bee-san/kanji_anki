from __future__ import annotations

from pathlib import Path

from kanji_leech_dashboard.study_content import (
    KANJIDIC2_FILE_NAME,
    KANJIVG_DIR_NAME,
    StudyContentProvider,
)

from .redesign_helpers import KANJIVG_FIXTURE, gzip_kanjidic_fixture


class FakeFetcher:
    def __init__(self, responses: dict[str, bytes]) -> None:
        self._responses = responses
        self.calls: list[str] = []

    def __call__(self, url: str) -> bytes:
        self.calls.append(url)
        if url not in self._responses:
            raise OSError(url)
        return self._responses[url]


def test_provider_auto_downloads_and_caches_kanjidic_and_kanjivg(tmp_path: Path) -> None:
    fetcher = FakeFetcher(
        {
            "https://example.invalid/kanjidic2.xml.gz": gzip_kanjidic_fixture(),
            "https://example.invalid/05b66.svg": KANJIVG_FIXTURE,
        }
    )
    provider = StudyContentProvider(
        user_files_dir=tmp_path,
        url_fetcher=fetcher,
        kanjidic_download_urls=("https://example.invalid/kanjidic2.xml.gz",),
        kanjivg_svg_url_template="https://example.invalid/{codepoint}.svg",
    )

    content = provider.get_content("学")
    repeat = provider.get_content("学")

    assert content.keyword == "study"
    assert content.primary_readings == ("ガク", "まな.ぶ")
    assert content.stroke_count == 8
    assert content.stroke_paths == ("M8 8 L32 8", "M18 18 L18 38")
    assert repeat.dictionary_source == KANJIDIC2_FILE_NAME
    assert repeat.stroke_source == "05b66.svg"
    assert fetcher.calls == [
        "https://example.invalid/kanjidic2.xml.gz",
        "https://example.invalid/05b66.svg",
    ]
    assert (tmp_path / KANJIDIC2_FILE_NAME).exists()
    assert (tmp_path / KANJIVG_DIR_NAME / "05b66.svg").exists()
