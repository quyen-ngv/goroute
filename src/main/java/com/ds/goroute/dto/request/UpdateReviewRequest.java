package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateReviewRequest {
    
    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    private Integer overallRating;
    
    @Min(value = 1, message = "Food rating must be between 1 and 5")
    @Max(value = 5, message = "Food rating must be between 1 and 5")
    private Integer foodRating;
    
    @Min(value = 1, message = "Price rating must be between 1 and 5")
    @Max(value = 5, message = "Price rating must be between 1 and 5")
    private Integer priceRating;
    
    @Min(value = 1, message = "Ambiance rating must be between 1 and 5")
    @Max(value = 5, message = "Ambiance rating must be between 1 and 5")
    private Integer ambianceRating;
    
    @Min(value = 1, message = "Service rating must be between 1 and 5")
    @Max(value = 5, message = "Service rating must be between 1 and 5")
    private Integer serviceRating;
    
    @Size(max = 2000, message = "Review text cannot exceed 2000 characters")
    private String text;
    
    private List<String> photos;
}
