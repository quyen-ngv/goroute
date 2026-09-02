package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/** Billing contact and free-form billing details (tax code, bank account label, address). */
@Data
public class UpdatePartnerBillingRequest {
    @Email @Size(max = 255) private String billingEmail;
    private Map<String, Object> billingDetails;
}
