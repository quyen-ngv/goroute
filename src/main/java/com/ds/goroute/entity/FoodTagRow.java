package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodTagRow {
    private UUID placeId;
    private UUID foodId;
    private String nameVi;
    private String nameEn;
    private String nameJa;
    private String nameKo;
}
