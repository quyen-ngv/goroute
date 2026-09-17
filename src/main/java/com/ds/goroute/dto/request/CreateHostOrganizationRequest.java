package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

import com.ds.goroute.type.OrganizationType;

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
    @ModeratedText(contentType = ModeratedContentType.PARTNER_LISTING, visibility = ModerationVisibility.PUBLIC)
    private String displayName;
    private OrganizationType organizationType = OrganizationType.BUSINESS;
    @Pattern(regexp = "[A-Z]{3}")
    private String defaultCurrency = "VND";
    @NotBlank @Size(max = 100)
    private String timezone = "Asia/Ho_Chi_Minh";
    @Email @Size(max = 320)
    private String contactEmail;
    /**
     * Required: this DTO only ever serves self-serve creation (`POST /partner/organizations`
     * and the console sign-up), where a reachable phone number is what the platform reviews
     * the organization on. Admin-created organizations use their own request type.
     */
    @NotBlank @Size(min = 8, max = 50) @Pattern(regexp = "[0-9 +().-]+")
    private String contactPhone;
    private Map<String, Object> settings;
}
