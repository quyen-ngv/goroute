package com.ds.goroute.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Data
public class AiTripCommitRequest {
    @NotBlank private String attemptId;
    private String tripDescription;
    @NotEmpty @Valid private List<Item> items;

    @Data
    public static class Item {
        @NotBlank private String type;
        private UUID placeId;
        @NotBlank private String name;
        @Min(1) private int dayNumber;
        @Min(0) private int sortOrder;
        private LocalTime startTime;
        private LocalTime endTime;
        private Integer endDayNumber;
        private String address;
        private BigDecimal latitude;
        private BigDecimal longitude;
        private String endAddress;
        private BigDecimal endLatitude;
        private BigDecimal endLongitude;
        private String category;
        private String transportMode;
        private String durationToNext;
        private Integer durationValueToNext;
        private String distanceToNext;
        private Integer distanceValueToNext;
        private String description;
        private String notes;
    }
}
