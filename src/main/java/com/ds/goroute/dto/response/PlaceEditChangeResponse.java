package com.ds.goroute.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** One field change of a place edit suggestion (plan §3.11); values in the §3.2 JSON shapes. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceEditChangeResponse {
    private UUID id;
    private String field;
    /** USER or ADMIN. */
    private String author;
    private String status;
    private String rejectReason;
    private JsonNode oldValue;
    private JsonNode newValue;
    private JsonNode appliedValue;
}
