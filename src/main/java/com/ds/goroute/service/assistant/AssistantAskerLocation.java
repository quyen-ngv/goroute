package com.ds.goroute.service.assistant;

import com.ds.goroute.dto.request.SenderLocationRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Where the asker's device was when the question was sent, rounded to {@link #SCALE} decimals
 * (about 11 m) before it leaves the request, so nothing downstream holds the exact fix.
 */
public record AssistantAskerLocation(BigDecimal lat, BigDecimal lng) {

    static final int SCALE = 4;
    /**
     * A fix coarser than this (an iOS "approximate location", a cell-tower guess) cannot answer
     * "near me": a search around it covers 3 km. It is treated as no position at all.
     */
    static final BigDecimal MAX_ACCURACY_METERS = BigDecimal.valueOf(3000);

    /** Null when nothing usable was sent. The request is already range-checked by Bean Validation. */
    public static AssistantAskerLocation of(SenderLocationRequest request) {
        if (request == null || request.getLatitude() == null || request.getLongitude() == null) return null;
        if (request.getAccuracyMeters() != null && request.getAccuracyMeters().compareTo(MAX_ACCURACY_METERS) > 0) {
            return null;
        }
        return new AssistantAskerLocation(request.getLatitude().setScale(SCALE, RoundingMode.HALF_UP),
                request.getLongitude().setScale(SCALE, RoundingMode.HALF_UP));
    }

    /** A position is personal data: it never prints, whatever logs the event that carries it. */
    @Override
    public String toString() {
        return "AssistantAskerLocation[hidden]";
    }
}
