package com.ds.goroute.dto.response;

import com.ds.goroute.dto.PlaceHoursDay;
import com.ds.goroute.dto.PlacePriceRange;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** GET /v1/api/places/{placeId}/edit-suggestions/context (plan §3.11). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceEditContextResponse {
    /** PLACE_EDIT/ENABLED is on or the caller is a beta user. */
    private boolean enabled;
    /** Whether an EDIT_INFO suggestion is accepted; a closure report may still be when false. */
    private boolean allowed;
    /** Null, DISABLED, PLACE_INACTIVE, PARTNER_PLACE or ACCOUNT_TOO_NEW. */
    private String reason;
    private String businessStatus;
    private List<String> lockedFields;
    /** The stored hours read back into the structured form; null when they cannot be read. */
    private List<PlaceHoursDay> hoursStructured;
    /** The stored price range read back; null when it holds no amounts. */
    private PlacePriceRange priceRange;
    private List<PendingChange> pendingMine;

    @Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
    public static class PendingChange {
        private UUID suggestionId;
        private UUID changeId;
        private String field;
        private JsonNode newValue;
    }
}
