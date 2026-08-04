package com.ds.goroute.type;

public enum PlaceAttributeGroup {
    UNIVERSAL("Universal suitability & access"),
    VIETNAM("Vietnam-specific practical details"),
    FOOD("Food, restaurant & cafe"),
    HOTEL("Hotel, hostel, homestay & resort"),
    ATTRACTION("Attraction, museum & landmark"),
    NATURE("Nature, beach, park & mountain"),
    THEME_PARK("Theme park & family entertainment"),
    SHOPPING("Shopping, market & night market"),
    NIGHTLIFE("Bar, club & nightlife"),
    WELLNESS("Spa, massage & wellness"),
    TEMPLE("Temple, pagoda & church"),
    TRANSPORT("Transport, airport & station"),
    AMBIENCE("Ambience tags");

    private final String label;

    PlaceAttributeGroup(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
