package com.ds.goroute.entity;

import com.ds.goroute.type.ContentVisibility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A set of places somebody put together and can share (SOC-04).
 *
 * <p>References saved items rather than copying the saved list. Sharing one collection must
 * never reveal anything else in that list: people save places they would not want listed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceCollection {
    private UUID id;
    private UUID ownerId;
    private String name;
    private String description;
    private String coverImageUrl;
    private ContentVisibility visibility;
    /** Unguessable, and cleared when the collection stops being public. */
    private String shareSlug;
    private Integer itemCount;
    private Integer viewCount;
    private Boolean isRemoved;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
