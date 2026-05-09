package com.ds.goroute.type;

public enum ReviewFlagType {
    HIGH_VELOCITY,           // Too many reviews in short time
    SUSPICIOUS_NEW_ACCOUNT,  // New account with extreme rating
    IP_CLUSTER,              // Multiple accounts from same IP
    NO_TRIP_CONTEXT,         // Review without trip context
    DUPLICATE_TEXT           // Similar text to other reviews
}
