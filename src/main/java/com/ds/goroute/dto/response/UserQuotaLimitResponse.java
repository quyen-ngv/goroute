package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserQuotaLimitResponse {
    private int globalLimit;
    private Integer overrideLimit;
    private int effectiveLimit;
}
