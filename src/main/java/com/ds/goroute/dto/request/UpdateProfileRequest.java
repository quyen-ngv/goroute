package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    @Size(max = 255)
    private String fullName;

    @Size(max = 50)
    private String username;

    @Size(max = 280)
    private String bio;

    private Map<String, String> socialLinks;

    private Boolean completeOnboarding;
}
