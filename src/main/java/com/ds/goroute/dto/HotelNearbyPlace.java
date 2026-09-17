package com.ds.goroute.dto;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.HotelNearbyCategory;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A place near the stay with its distance, typed in by the partner or an operator. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotelNearbyPlace {
    @ModeratedText(contentType = ModeratedContentType.PARTNER_LISTING, visibility = ModerationVisibility.PUBLIC)
    @NotBlank @Size(max = 200)
    private String name;

    @NotNull
    private HotelNearbyCategory category;

    @NotNull @Min(0) @Max(200_000)
    private Integer distanceMeters;
}
