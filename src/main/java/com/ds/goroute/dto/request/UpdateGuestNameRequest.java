package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateGuestNameRequest {
    
    @NotBlank(message = "Guest name is required")
    @Size(min = 1, max = 255, message = "Guest name must be between 1 and 255 characters")
    private String guestName;
}
