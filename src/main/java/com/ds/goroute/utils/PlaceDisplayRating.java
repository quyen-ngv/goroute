package com.ds.goroute.utils;

import com.ds.goroute.entity.Place;

import java.math.BigDecimal;

/**
 * The star rating the app shows for a place, sent in every response field named
 * {@code adjustedRating} / {@code placeAdjustedRating}.
 *
 * <p>Today that is Google Maps' own rating ({@code places.review_rating}): the app has too few
 * reviews of its own yet, and Google's number is what introduces a place to a new user. The
 * recalculated {@code places.adjusted_rating} is still computed and stored, and still orders
 * "by rating" lists and searches, but is no longer shown. When the app has enough reviews,
 * switch the source here; no client change is needed.
 */
public final class PlaceDisplayRating {

    private PlaceDisplayRating() {
    }

    public static BigDecimal of(Place place) {
        return place == null ? null : of(place.getReviewRating());
    }

    public static BigDecimal of(BigDecimal googleRating) {
        return googleRating;
    }
}
