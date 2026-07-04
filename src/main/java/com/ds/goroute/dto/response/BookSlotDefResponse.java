package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookSlotDefResponse {
    private String slotId;
    private String type;
    private Double x;
    private Double y;
    private Double w;
    private Double h;
    private Double rotation;
    private Integer zIndex;
}
