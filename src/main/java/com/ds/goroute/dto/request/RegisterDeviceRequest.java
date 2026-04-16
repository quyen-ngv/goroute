package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotBlank;
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
    private String fcmToken;
    
    @NotBlank(message = "Device type is required")
    private String deviceType; // ios | android
    
    private String deviceName;
}
