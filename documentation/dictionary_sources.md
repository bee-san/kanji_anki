# Dictionary Source Refresh

Kani bundles compact offline dictionary assets generated from EDRDG source XML:

- `app/src/main/assets/dictionaries/jmdict_e_words.tsv.gz`
- `app/src/main/assets/dictionaries/kanjidic2_kanji.tsv.gz`
- `app/src/main/assets/dictionaries/dictionary_sources.json`

Refresh command:

```sh
curl -fL http://ftp.edrdg.org/pub/Nihongo/JMdict_e.gz -o /tmp/JMdict_e.gz
curl -fL https://www.edrdg.org/kanjidic/kanjidic2.xml.gz -o /tmp/kanjidic2.xml.gz
python3 tools/generate_dictionary_assets.py \
  --jmdict /tmp/JMdict_e.gz \
  --kanjidic2 /tmp/kanjidic2.xml.gz \
  --output-dir app/src/main/assets/dictionaries \
  --fetch-date "$(date +%F)"
```

The generated manifest records upstream URLs, fetch dates, source SHA-256
hashes, imported fields, asset hashes, and license notes. SKIP query codes are
not imported because EDRDG documents separate SKIP licensing conditions.
