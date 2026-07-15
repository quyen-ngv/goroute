package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ApplyReferralCodeRequest {
    @NotBlank
    private String code;
}
