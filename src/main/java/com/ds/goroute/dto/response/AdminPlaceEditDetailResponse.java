package com.ds.goroute.dto.response;

import com.ds.goroute.dto.PlaceHoursDay;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** GET /v1/api/admin/place-edit-suggestions/{id} and the review result (plan §3.11). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPlaceEditDetailResponse {
    private PlaceEditSuggestionResponse suggestion;
    private Submitter submitter;
    private Reviewer reviewer;
    private String reviewChannel;
    private PlaceSnapshot place;
    private List<OtherPending> otherPending;

    @Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
    public static class Submitter {
        private UUID id;
        private String name;
        private String avatar;
        private LocalDateTime createdAt;
        private long approvedCount;
        private long rejectedCount;
    }

    @Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
    public static class Reviewer {
        private UUID id;
        private String name;
    }

    /** The place as it is now, every field a suggestion can change. */
    @Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
    public static class PlaceSnapshot {
        private UUID id;
        private String title;
        /** The name shown in the caller's language (place_translations). */
        private String displayName;
        /** The vi translation's name. */
        private String originalName;
        private String googleMapsLink;
        private String address;
        private BigDecimal latitude;
        private BigDecimal longitude;
        private boolean hasVerificationGeometry;
        private String placeGroup;
        private List<String> subTypes;
        private String phone;
        private String website;
        /** The stored display text, e.g. "₫50,000–100,000". */
        private String priceRange;
        private Map<String, List<String>> openHours;
        private List<PlaceHoursDay> hoursStructured;
        private String description;
        private List<String> searchAliases;
        private String status;
        private String businessStatus;
        private String visibilityStatus;
        private List<String> lockedFields;
        /** PlaceAttributeCatalog.adminAttributes. */
        private JsonNode attributes;
    }

    @Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
    public static class OtherPending {
        private UUID id;
        private String userName;
        private List<String> fields;
        private LocalDateTime createdAt;
    }
}
