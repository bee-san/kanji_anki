package dev.bee.kanjianki.data;

import android.content.Context;

import dev.bee.kanjianki.core.DictionaryLookup;

import java.io.IOException;

public final class DictionaryAssets {
    public static final String DATABASE_ASSET_NAME = "kanji_dictionary.db";
    public static final String DATABASE_SHA256_ASSET_NAME = "kanji_dictionary.db.sha256";
    public static final String DATABASE_ASSET = "dictionaries/" + DATABASE_ASSET_NAME;
    public static final String DATABASE_SHA256_ASSET = "dictionaries/" + DATABASE_SHA256_ASSET_NAME;
    public static final String SOURCES_ASSET = "dictionaries/dictionary_sources.json";

    private DictionaryAssets() {
    }

    public static DictionaryLookup load(Context context) {
        try {
            return DictionaryStore.open(context);
        } catch (IOException error) {
            return DictionaryLookup.empty();
        }
    }
}
