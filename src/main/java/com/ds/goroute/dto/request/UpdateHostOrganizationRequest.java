package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

import com.ds.goroute.type.OrganizationOperationalStatus;
import com.ds.goroute.type.OrganizationType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class UpdateHostOrganizationRequest {
    @NotBlank @Size(max = 500)
    private String legalName;
    @NotBlank @Size(max = 500)
    @ModeratedText(contentType = ModeratedContentType.PARTNER_LISTING, visibility = ModerationVisibility.PUBLIC)
    private String displayName;
    private OrganizationType organizationType;
    private OrganizationOperationalStatus operationalStatus;
    @Pattern(regexp = "[A-Z]{3}")
    private String defaultCurrency;
    @NotBlank @Size(max = 100)
    private String timezone;
    @Email @Size(max = 320)
    private String contactEmail;
    @Size(max = 50)
    private String contactPhone;
    private Map<String, Object> settings;
    private Long expectedVersion;
}
