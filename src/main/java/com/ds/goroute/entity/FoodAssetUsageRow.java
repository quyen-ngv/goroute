package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** One food that lists a library asset among its core ingredients. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodAssetUsageRow {
    private UUID assetId;
    private UUID foodId;
    private String nameVi;
    private String nameEn;
}
