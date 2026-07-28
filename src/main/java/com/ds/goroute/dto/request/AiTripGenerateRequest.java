package com.ds.goroute.dto.request;

import com.ds.goroute.type.PlaceGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
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

    @Valid
    @Size(max = 10, message = "A trip can contain at most 10 destinations")
    private List<AiTripDestinationRequest> destinations;

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

    // Enhanced fields from best practices
    @Size(max = 500)
    private String groupComposition; // "Family of 4 (2 adults, 2 kids aged 8-12)"

    private BigDecimal budgetMin;
    private BigDecimal budgetMax;

    @Size(max = 10)
    private String budgetCurrency; // "VND", "USD"

    @Size(max = 50)
    private String travelStyle; // "Relaxed", "Adventure", "Luxury", "Cultural", "Family-friendly"

    private List<String> activityTypes; // ["Food", "Nature", "Culture", "Adventure", "Photography", "Shopping", "Wellness"]

    private List<String> dietaryRestrictions; // ["Vegetarian", "Halal", "Gluten-free", "Vegan", "No-pork"]

    private List<String> mobilityConsiderations; // ["Elderly-friendly", "Wheelchair-accessible", "Kid-friendly"]

    @Builder.Default
    private Boolean includeBackupActivities = true; // For indoor alternatives during bad weather
}
