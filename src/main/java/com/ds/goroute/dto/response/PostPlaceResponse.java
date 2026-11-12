package com.ds.goroute.dto.response;

import com.ds.goroute.type.PlaceGroup;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

/** {@code PostPlace} of the contract: a catalogue place the post is tagged with, as a card. */
@Value
@Builder
public class PostPlaceResponse {
    UUID placeId;
    String name;
    String address;
    String thumbnail;
    BigDecimal latitude;
    BigDecimal longitude;
    /** The displayed rating (PlaceDisplayRating). */
    BigDecimal rating;
    PlaceGroup placeGroup;
}
