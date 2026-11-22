package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The caller's self-declared birth year after an update. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BirthYearResponse {

    private Integer birthYear;
}
