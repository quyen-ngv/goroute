package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

/** Minimal province option for operator configuration forms. */
@Data
@Builder
public class PassportProvinceOptionResponse {
    private String code;
    private String name;
}
