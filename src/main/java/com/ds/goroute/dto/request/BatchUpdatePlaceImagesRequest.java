package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchUpdatePlaceImagesRequest {
    
    @NotNull(message = "Places list cannot be null")
    private List<PlaceImageUpdate> places;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlaceImageUpdate {
        @NotNull(message = "Place ID cannot be null")
        private UUID id;
        
        private String placeId; // Google Place ID (optional, for verification)
        
        private String thumbnail;
        
        private String images; // JSON string
    }
}
