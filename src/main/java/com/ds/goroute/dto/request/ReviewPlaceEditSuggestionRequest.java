package com.ds.goroute.dto.request;

import com.ds.goroute.type.PlaceEditDecisionAction;
import com.ds.goroute.type.PlaceEditField;
import com.ds.goroute.type.PlaceEditRejectReason;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * {@code POST /v1/api/admin/place-edit-suggestions/{id}/review} (plan §3.10–3.11). Every pending
 * change of the suggestion needs a decision. {@code appliedValue} null applies the submitted value;
 * OPEN_HOURS is sent as {@code {"structured": [...]}}, PRICE_RANGE as {@code {min, max, currency}},
 * ATTRIBUTES as {@code {"attributes": {"<key>": {"value": ..., "description": ..., "source_found": ...}}}}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewPlaceEditSuggestionRequest {

    @Valid
    @Size(max = 20)
    private List<Decision> decisions;

    @Valid
    @Size(max = 20)
    private List<AdminChange> adminChanges;

    /** Required when nothing is applied. */
    private PlaceEditRejectReason rejectReason;

    /** Internal note; never shown to the submitter. */
    @Size(max = 500)
    private String note;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Decision {
        @NotNull
        private UUID changeId;
        @NotNull
        private PlaceEditDecisionAction action;
        /** Null (or JSON null) applies the change's new value. */
        private JsonNode appliedValue;
        /** NAME / DESCRIPTION only: the translation locale to write; default the suggestion's. */
        @Size(max = 10)
        private String locale;
        private PlaceEditRejectReason rejectReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminChange {
        @NotNull
        private PlaceEditField field;
        @NotNull
        private JsonNode value;
        @Size(max = 10)
        private String locale;
    }
}
