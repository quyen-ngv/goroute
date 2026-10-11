package com.ds.goroute.dto.request;

import com.ds.goroute.type.RouteProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * One day's stops in visiting order, and how each leg between two consecutive stops is
 * travelled: {@code profiles.get(i)} is the leg from {@code points.get(i)} to {@code points.get(i + 1)}.
 */
@Data
public class RouteDayRequest {

    @NotNull
    @Size(min = 2, max = 50)
    @Valid
    private List<RoutePoint> points;

    @NotNull
    @Size(min = 1, max = 49)
    private List<@NotNull RouteProfile> profiles;

    @Data
    public static class RoutePoint {
        @NotNull
        @DecimalMin("-90")
        @DecimalMax("90")
        private Double latitude;

        @NotNull
        @DecimalMin("-180")
        @DecimalMax("180")
        private Double longitude;
    }
}
