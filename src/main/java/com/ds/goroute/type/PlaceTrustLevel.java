package com.ds.goroute.type;

public enum PlaceTrustLevel {
    TRUSTED,    // >= 0.80
    MODERATE,   // 0.55 â€“ 0.79
    CAUTION,    // 0.30 â€“ 0.54
    SUSPICIOUS  // < 0.30
}
