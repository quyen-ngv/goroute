package com.ds.goroute.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A place as a map pin draws it, and as the card listing it shows it: where it is, its name in
 * the caller's language, its kind, its address, a few photos and how much travellers use it.
 * Field names match {@link PlaceResponse}, so a client reads both the same way.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlaceMapPinResponse {
    private UUID id;
    private String placeId;
    private String name;
    private String title;
    private String placeGroup;
    private List<String> subTypes;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String thumbnail;
    /** The first few photos, for a card to swipe through; at most {@code MAX_IMAGES}. */
    private List<PlaceImagesDto> images;
    private BigDecimal reviewRating;
    private BigDecimal adjustedRating;
    private Integer reviewCount;
    /** Trips that use the place (V225). */
    private Integer tripUseCount;
    /** Check-ins on the place (V225). */
    private Integer checkinCount;

    /** Photos a pin carries: enough to swipe through on a card, few enough for a tile. */
    public static final int MAX_IMAGES = 5;
}
