package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavePlaceRequest {
    
    @NotBlank(message = "Place ID is required")
    private String placeId;

    private String itemType;
    
    @NotBlank(message = "Name is required")
    private String name;
    
    private String address;
    private Double lat;
    private Double lng;
    private String category;
    private Double rating;
    private String photoUrl;
    private List<String> tags;
}
