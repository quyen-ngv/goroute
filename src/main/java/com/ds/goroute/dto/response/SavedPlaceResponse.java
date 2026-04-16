package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedPlaceResponse {
    private UUID id;
    private String placeId;
    private String name;
    private String address;
    private Double lat;
    private Double lng;
    private String category;
    private Double rating;
    private String photoUrl;
    private List<String> tags;
    private LocalDateTime createdAt;
}
