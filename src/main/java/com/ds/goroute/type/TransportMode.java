package com.ds.goroute.type;

public enum TransportMode {
    WALKING("🚶"),
    MOTORBIKE("🏍️"),
    CAR("🚗"),
    TAXI("🚕"),
    BUS("🚌"),
    TRAIN("🚂"),
    FERRY("⛴️"),
    CANOE("🚤"),
    PLANE("✈️");

    private final String emoji;

    TransportMode(String emoji) {
        this.emoji = emoji;
    }

    public String getEmoji() {
        return emoji;
    }
}
