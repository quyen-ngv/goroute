package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code PUT /v1/api/users/me/birth-year}. Self-declared and only used to check meetup age rules;
 * never shown to other users. The range (1900..current year) is checked by the service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBirthYearRequest {

    @NotNull
    private Integer birthYear;
}
