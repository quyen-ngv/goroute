package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class CreateHostOrganizationRequest {
    @NotBlank @Size(max = 500)
    private String legalName;
    @NotBlank @Size(max = 500)
    private String displayName;
    @Pattern(regexp = "INDIVIDUAL|BUSINESS")
    private String organizationType = "BUSINESS";
    @Pattern(regexp = "[A-Z]{3}")
    private String defaultCurrency = "VND";
    @NotBlank @Size(max = 100)
    private String timezone = "Asia/Ho_Chi_Minh";
    @Email @Size(max = 320)
    private String contactEmail;
    @Size(max = 50)
    private String contactPhone;
    private Map<String, Object> settings;
}
