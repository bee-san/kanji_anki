# Goal 178 schema corpus

This directory is the executable Android/desktop persistence contract.

- `schema-v34.sql` is the canonical fresh schema. The v34 migration adds a
  conditional settings row but no fresh-schema object, so its DDL derives from
  the provenance-pinned v33 schema and is checked against a real fresh
  `LocalStore`.
- `historical-*.db.gz` files are genuine SQLite databases created from the
  historical DDL pinned under `goal165`, populated while still at that version,
  and then compressed with deterministic gzip metadata. SQLite may choose a
  different byte-level page layout across engine versions, so regeneration is
  checked by schema/data dump and `user_version`, not by generated file bytes.
  They are not current databases with a lowered `user_version`.
- `historical-databases.tsv` pins source and database hashes.
- `migration-dependencies.tsv` records every migration's clock, defaults,
  input-data, and Android SQLite dependency for Goal 179 injection.
- `schema-v34.properties` freezes schema and persisted format constants.

All rows are synthetic and use the `goal178` marker. Regenerate generated
artifacts with:

```sh
python3 tools/generate_schema_corpus.py
```
