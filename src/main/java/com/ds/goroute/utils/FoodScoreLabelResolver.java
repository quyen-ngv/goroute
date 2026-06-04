package com.ds.goroute.utils;

public final class FoodScoreLabelResolver {

    private FoodScoreLabelResolver() {
    }

    public static String toLabelKey(Integer score) {
        if (score == null || score < 50) {
            return null;
        }
        if (score >= 90) {
            return "food.score.signature";
        }
        if (score >= 70) {
            return "food.score.popular";
        }
        return "food.score.common";
    }
}
