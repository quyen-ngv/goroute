package com.ds.goroute.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ActivityItineraryItem {
    @Size(max = 500)
    private String title;

    @Size(max = 10000)
    private String content;

    @Size(max = 20)
    private List<@Size(max = 2000) String> images;
}
