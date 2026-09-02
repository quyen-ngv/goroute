package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class RedeemVoucherRequest {
    @NotNull private UUID organizationId;
    @NotBlank private String voucherCode;
}
