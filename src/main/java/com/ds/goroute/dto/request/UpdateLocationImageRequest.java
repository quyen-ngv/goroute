package com.ds.goroute.dto.request;

import com.ds.goroute.dto.LocationDescriptionSection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLocationImageRequest {
    private String fullAddress;

    @Size(max = 255, message = "Slogan must not exceed 255 characters")
    private String slogan;

    @Valid
    private List<LocationDescriptionSection> description;

    private String imageUrl;
    private String avatarUrl;
    @Size(max = 10)
    private String provinceCode;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer priority;

    /** Radius in km within which a place/tour/hotel counts as part of this area. */
    @DecimalMin(value = "0.1", message = "Coverage radius must be greater than 0")
    @DecimalMax(value = "500", message = "Coverage radius must not exceed 500 km")
    private BigDecimal coverageRadiusKm;
}
