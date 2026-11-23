package com.ds.goroute.enums;

/** Which location images a catalogue request wants. REGION is what apps before V296 expect. */
public enum LocationImageKindFilter {
    REGION,
    COUNTRY,
    ALL;

    public boolean accepts(LocationImageKind kind) {
        return this == ALL || name().equals(kind.name());
    }
}
