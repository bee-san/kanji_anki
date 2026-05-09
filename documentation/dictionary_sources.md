# Dictionary Source Refresh

Kani bundles a compact offline SQLite dictionary generated from KANJIDIC2 and
the checked-in Jiten kanji rank CSV:

- `app/src/main/assets/dictionaries/kanji_dictionary.db`
- `app/src/main/assets/dictionaries/dictionary_sources.json`
- `app/src/main/assets/dictionaries/kanji_dictionary.db.sha256`

Refresh command:

```sh
curl -fL https://www.edrdg.org/kanjidic/kanjidic2.xml.gz -o /tmp/kanjidic2.xml.gz
python3 tools/generate_dictionary_assets.py \
  --kanjidic2 /tmp/kanjidic2.xml.gz \
  --jiten-ranks tools/data/jiten_kanji_rank.csv \
  --output-dir app/src/main/assets/dictionaries \
  --fetch-date "$(date +%F)"
```

The generated manifest records upstream URLs, fetch dates, source SHA-256
hashes, imported fields, asset hashes, and license notes. The future dictionary
update package shape is the DB, this manifest, and the DB checksum sidecar. SKIP
query codes are not imported because EDRDG documents separate SKIP licensing
conditions. Word-level dictionary data is not bundled; study `From:` lines come
from Anki examples.
