package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDeviceRequest {
    @Size(max = 4096, message = "FCM token is too long")
    private String fcmToken;
    @Size(max = 20, message = "Language is too long")
    private String language;
    private Boolean isActive;
}
