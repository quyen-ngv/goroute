package com.ds.goroute.type;

/**
 * Which shelf of the shared image library ({@code asset_library}) an asset sits on. Keys are
 * unique per collection, so {@code lemongrass} can exist once as an ingredient and once as a banner.
 */
public enum AssetCollection {
    /** Ingredient icons shown on food detail ({@code foods.core_ingredients[].assetId}). */
    INGREDIENT,
    BANNER,
    PROMOTION,
    GENERAL
}
