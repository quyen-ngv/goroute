package com.ds.goroute.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FoodCoreIngredientDto {
    /**
     * The ingredient's icon in the shared asset library (collection INGREDIENT). When set, the
     * image and the name are read from the asset when the food is served, and {@code image} is
     * not stored; {@code name} stays as the fallback and {@code description} is per dish.
     */
    private UUID assetId;
    private String image;
    private String name;
    private String description;
}
