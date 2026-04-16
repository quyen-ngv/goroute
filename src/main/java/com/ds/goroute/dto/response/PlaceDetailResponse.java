package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceDetailResponse {
    private String placeId;
    private String name;
    private String address;
    private Double lat;
    private Double lng;
    private Double rating;
    private Integer totalRatings;
    private Integer priceLevel;
    private List<String> types;
    private String phoneNumber;
    private String website;
    private OpeningHours openingHours;
    private List<Photo> photos;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpeningHours {
        private Boolean openNow;
        private List<String> weekdayText;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Photo {
        private String photoReference;
        private Integer width;
        private Integer height;
        private String url;
    }
}
