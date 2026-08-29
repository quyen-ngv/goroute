package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import com.ds.goroute.type.PlaceGroup;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * What the operator confirmed about a cluster before it enters the catalogue.
 *
 * <p>The name and coordinates are the operator's, not the crowd's: the most common name in
 * a cluster is a starting suggestion, and somebody has to actually look at the photos
 * before this becomes a row that Explore, search and itinerary planning all read.
 */
@Data
public class PromoteCheckinClusterRequest {

    @NotBlank(message = "A name is required")
    @Size(max = 500)
    @ModeratedText(contentType = ModeratedContentType.USER_PLACE, visibility = ModerationVisibility.PUBLIC)
    private String title;

    @Size(max = 1000)
    @ModeratedText(contentType = ModeratedContentType.USER_PLACE, visibility = ModerationVisibility.PUBLIC)
    private String address;

    private PlaceGroup placeGroup;

    @DecimalMin("-90.0") @DecimalMax("90.0")
    private BigDecimal latitude;

    @DecimalMin("-180.0") @DecimalMax("180.0")
    private BigDecimal longitude;

    @Size(max = 10)
    private String provinceCode;

    @Size(max = 1000)
    private String note;
}
