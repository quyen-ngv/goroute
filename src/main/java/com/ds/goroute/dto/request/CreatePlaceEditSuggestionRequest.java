package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.dto.PlaceHoursDay;
import com.ds.goroute.dto.PlacePriceRange;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import com.ds.goroute.type.PlaceEditField;
import com.ds.goroute.type.PlaceEditType;
import com.ds.goroute.type.PlaceGroup;
import com.ds.goroute.type.PlaceStatusReportReason;
import com.ds.goroute.type.PlaceSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * {@code POST /v1/api/places/{placeId}/edit-suggestions} (plan §3.10). A field absent from
 * {@link #changes} is not suggested; clearing the phone or the website goes in {@link #clearFields}.
 * A STATUS_REPORT carries only {@code changes.businessStatus}, the description and photos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePlaceEditSuggestionRequest {

    @NotNull
    private PlaceEditType type;

    /** The submitter's own words; may be the whole suggestion (at least 10 characters then). */
    @Size(max = 1000)
    @ModeratedText(contentType = ModeratedContentType.USER_PLACE, visibility = ModerationVisibility.PUBLIC)
    private String description;

    @Valid
    private Changes changes;

    /** Only PHONE and WEBSITE may be cleared. */
    @Size(max = 2)
    private List<PlaceEditField> clearFields;

    /** At most three storage URLs the submitter uploaded through /v1/api/files/upload. */
    @Size(max = 3)
    private List<@Size(max = 1000) String> evidencePhotos;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Changes {

        @Size(min = 1, max = 200)
        @ModeratedText(contentType = ModeratedContentType.USER_PLACE, visibility = ModerationVisibility.PUBLIC)
        private String name;

        @Size(min = 1, max = 200)
        @ModeratedText(contentType = ModeratedContentType.USER_PLACE, visibility = ModerationVisibility.PUBLIC)
        private String alias;

        /** The place's description (DESCRIPTION), 20..2000 characters. */
        @Size(min = 20, max = 2000)
        @ModeratedText(contentType = ModeratedContentType.USER_PLACE, visibility = ModerationVisibility.PUBLIC)
        private String placeDescription;

        @Valid
        private Category category;

        @Size(min = 1, max = 500)
        @ModeratedText(contentType = ModeratedContentType.USER_PLACE, visibility = ModerationVisibility.PUBLIC)
        private String address;

        @Valid
        private Location location;

        /** All seven days, structured (plan §3.4); the server builds the stored strings. */
        @Size(min = 7, max = 7)
        private List<PlaceHoursDay> openHours;

        @Size(min = 1, max = 50)
        private String phone;

        @Size(min = 1, max = 500)
        private String website;

        private PlacePriceRange priceRange;

        @Valid
        private BusinessStatus businessStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Category {
        @NotNull
        private PlaceGroup placeGroup;
        @Size(max = 10)
        private List<PlaceSubType> subTypes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Location {
        @NotNull
        @DecimalMin("-90")
        @DecimalMax("90")
        private BigDecimal latitude;
        @NotNull
        @DecimalMin("-180")
        @DecimalMax("180")
        private BigDecimal longitude;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BusinessStatus {
        @NotNull
        private PlaceStatusReportReason reason;
        /** Required for DUPLICATE: the ACTIVE place this one repeats. */
        private UUID duplicateOfPlaceId;
    }
}
