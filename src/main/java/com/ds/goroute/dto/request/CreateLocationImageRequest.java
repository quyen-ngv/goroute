package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateLocationImageRequest {
    @NotBlank(message = "Full address is required")
    private String fullAddress;
    
    @NotBlank(message = "Image URL is required")
    private String imageUrl;
    
    @NotNull(message = "Priority is required")
    private Integer priority;
}
