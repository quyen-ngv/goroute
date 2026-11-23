package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Where the caller is from. Both fields are null until they have said so. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserHomeResponse {

    /** ISO 3166-1 alpha-2. */
    private String countryCode;

    /** The country's name in the request's language. */
    private String countryName;
}
