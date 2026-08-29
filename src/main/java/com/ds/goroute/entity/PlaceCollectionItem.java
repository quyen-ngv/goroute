package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One place in a collection.
 *
 * <p>Carries its own display fields so a collection can also hold somewhere the author
 * typed themselves, and so an entry still renders if the catalogue row behind it stops
 * being public -- disappearing silently would look like the collection lost something.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceCollectionItem {
    private UUID id;
    private UUID collectionId;
    private UUID placeId;
    private String displayName;
    private String displayNote;
    private BigDecimal latitude;
    private BigDecimal longitude;
    /** Order is content: "top 10" says something a random list does not. */
    private Integer position;
    private LocalDateTime createdAt;
}
