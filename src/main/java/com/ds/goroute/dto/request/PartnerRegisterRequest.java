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

/** Self-serve partner sign-up from the console login page: one owner account plus one organization. */
@Data
public class PartnerRegisterRequest {
    @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "[A-Za-z0-9._-]+")
    private String username;
    @NotBlank @Email @Size(max = 320)
    private String email;
    @NotBlank @Size(min = 8, max = 100)
    private String password;
    @NotBlank @Size(max = 255)
    private String fullName;
    @NotBlank @Size(max = 500)
    private String legalName;
    @NotBlank @Size(max = 500)
    @ModeratedText(contentType = ModeratedContentType.PARTNER_LISTING, visibility = ModerationVisibility.PUBLIC)
    private String displayName;
    private OrganizationType organizationType = OrganizationType.BUSINESS;
    @NotBlank @Size(max = 100)
    private String timezone = "Asia/Ho_Chi_Minh";
    /**
     * Required: a self-serve sign-up proves nothing about who is behind it, so the one
     * contact the platform can actually call back on before approving is not optional.
     */
    @NotBlank @Size(min = 8, max = 50) @Pattern(regexp = "[0-9 +().-]+")
    private String contactPhone;
}
