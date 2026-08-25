package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterDeviceRequest {

    @NotBlank(message = "FCM token is required")
    @Size(max = 4096, message = "FCM token is too long")
    private String fcmToken;

    @NotBlank(message = "Device type is required")
    @Pattern(regexp = "(?i)ios|android", message = "Device type must be ios or android")
    private String deviceType;

    @Size(max = 200, message = "Device name is too long")
    private String deviceName;

    @Size(max = 20, message = "Language is too long")
    private String language;
}
