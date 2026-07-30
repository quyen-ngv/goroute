package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateActivityOrderStatusRequest {
    @NotBlank @Pattern(regexp="CONFIRMED|CHECKED_IN|COMPLETED|EXPIRED|FAILED|CANCELLED_BY_GUEST|CANCELLED_BY_HOST|CANCELLED_BY_PLATFORM|NO_SHOW")
    private String orderStatus;
    private String reason;
    private Long expectedVersion;
}
