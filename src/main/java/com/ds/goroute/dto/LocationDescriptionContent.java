package com.ds.goroute.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationDescriptionContent {
    private String title;

    @Size(max = 5000, message = "Location description content must not exceed 5000 characters")
    private String content;
}
