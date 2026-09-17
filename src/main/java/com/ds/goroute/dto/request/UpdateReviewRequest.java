package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

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

    @Min(value = 1, message = "Location rating must be between 1 and 5")
    @Max(value = 5, message = "Location rating must be between 1 and 5")
    private Integer locationRating;

    @Min(value = 1, message = "Cleanliness rating must be between 1 and 5")
    @Max(value = 5, message = "Cleanliness rating must be between 1 and 5")
    private Integer cleanlinessRating;

    @Min(value = 1, message = "Facilities rating must be between 1 and 5")
    @Max(value = 5, message = "Facilities rating must be between 1 and 5")
    private Integer facilitiesRating;
    
    @Size(max = 2000, message = "Review text cannot exceed 2000 characters")
    @ModeratedText(contentType = ModeratedContentType.REVIEW, visibility = ModerationVisibility.PUBLIC)
    private String text;
    
    private List<String> photos;
}
