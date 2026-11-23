package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** Where the caller is from: their country. */
@Data
public class UpdateUserHomeRequest {

    @NotBlank(message = "Country code is required")
    @Pattern(regexp = "^[A-Za-z]{2}$", message = "Country code must be two letters")
    private String countryCode;
}
