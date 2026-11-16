package com.ds.goroute.type;

/**
 * Where a place name came from. A stored row is overwritten only by a source of the same or a
 * higher {@link #rank()}: a name fixed by hand is never replaced by a scraped one.
 */
public enum TranslationSource {
    MANUAL(2),
    LEGACY(0),
    GOOGLE(1),
    WIKIDATA(1);

    private final int rank;

    TranslationSource(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}
