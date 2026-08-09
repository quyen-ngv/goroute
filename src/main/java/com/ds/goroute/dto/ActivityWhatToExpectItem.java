package com.ds.goroute.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ActivityWhatToExpectItem {
    @Size(max = 5000)
    private String text;

    @Size(max = 2000)
    private String imageUrl;
}
