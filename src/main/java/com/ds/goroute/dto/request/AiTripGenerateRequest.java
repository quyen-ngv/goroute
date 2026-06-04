package com.ds.goroute.dto.request;

import com.ds.goroute.type.PlaceGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTripGenerateRequest {
    @Size(max = 255)
    private String tripName;

    private String cityId;

    @NotBlank(message = "City name is required")
    @Size(max = 255)
    private String cityName;

    private BigDecimal cityLat;
    private BigDecimal cityLng;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    @Positive(message = "Day count must be positive")
    private Integer dayCount;

    private List<PlaceGroup> placeGroups;

    @Builder.Default
    private String pace = "BALANCED";

    @Size(max = 2000)
    private String preferenceText;
}
