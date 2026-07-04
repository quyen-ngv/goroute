package com.ds.goroute.dto.response;

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
public class TripBookResponse {
    private UUID bookId;
    private UUID tripId;
    private String status;
    private List<BookPageResponse> pages;
    private List<BookSkeletonResponse> skeletons;
}
