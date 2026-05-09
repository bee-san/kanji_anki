package dev.bee.kanjianki.data;

import android.content.Context;

import dev.bee.kanjianki.core.DictionaryLookup;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

public final class DictionaryAssets {
    private static final String WORDS_ASSET = "dictionaries/jmdict_e_words.tsv.gz";
    private static final String KANJI_ASSET = "dictionaries/kanjidic2_kanji.tsv.gz";
    public static final String SOURCES_ASSET = "dictionaries/dictionary_sources.json";

    private DictionaryAssets() {
    }

    public static DictionaryLookup load(Context context) {
        try (InputStream words = new GZIPInputStream(context.getAssets().open(WORDS_ASSET));
             InputStream kanji = new GZIPInputStream(context.getAssets().open(KANJI_ASSET))) {
            return DictionaryLookup.fromTsv(words, kanji);
        } catch (IOException error) {
            return DictionaryLookup.empty();
        }
    }
}
