package com.ds.goroute.constant;

import java.util.List;

/**
 * Constants for AI trip planning based on travel industry best practices
 */
public class AiTripConstants {

    public static final List<String> VALID_TRAVEL_STYLES = List.of(
            "Relaxed", "Adventure", "Luxury", "Cultural", "Family-friendly", 
            "Budget", "Backpacking", "Romantic", "Solo", "Business"
    );

    public static final List<String> VALID_ACTIVITY_TYPES = List.of(
            "Food", "Nature", "Culture", "Adventure", "Photography", 
            "Shopping", "Wellness", "Nightlife", "Sports", "Beach", 
            "Mountains", "Historical", "Religious", "Educational"
    );

    public static final List<String> VALID_DIETARY_RESTRICTIONS = List.of(
            "Vegetarian", "Vegan", "Halal", "Kosher", "Gluten-free", 
            "Dairy-free", "Nut-free", "Seafood-free", "No-pork", "No-beef"
    );

    public static final List<String> VALID_MOBILITY_CONSIDERATIONS = List.of(
            "Elderly-friendly", "Wheelchair-accessible", "Kid-friendly", 
            "Stroller-accessible", "No-stairs", "Ground-floor-only"
    );

    public static final List<String> SUPPORTED_CURRENCIES = List.of(
            "VND", "USD", "EUR", "GBP", "JPY", "KRW", "CNY", "THB", "SGD", "AUD"
    );

    private AiTripConstants() {
        // Utility class
    }
}
