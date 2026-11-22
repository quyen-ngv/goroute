package com.ds.goroute.service.assistant;

import java.util.List;
import java.util.Locale;

/**
 * Pengoo's fixed lines (no model call). Stored as the message text so any client can read them;
 * the app shows its own translation from the message's block instead whenever it knows the block,
 * so this text only needs Vietnamese and an English fallback.
 */
final class AssistantNotices {

    private AssistantNotices() {
    }

    static boolean isVietnamese(String language) {
        return language != null && language.toLowerCase(Locale.ROOT).startsWith("vi");
    }

    static String creditExhausted(String language) {
        return isVietnamese(language)
                ? "Bạn đã hết credit AI nên Pengoo chưa trả lời được. Credit sẽ được nạp lại ở kỳ tới, hoặc bạn có thể mua thêm."
                : "You are out of AI credits, so Pengoo can't answer yet. Your credits refill next period, or you can buy more.";
    }

    static String creditStopped(String language) {
        return isVietnamese(language)
                ? "Bạn đã hết credit AI nên câu trả lời này chưa hoàn chỉnh."
                : "You ran out of AI credits, so this answer is incomplete.";
    }

    static String busy(String language) {
        return isVietnamese(language)
                ? "Pengoo đang trả lời một câu khác của bạn. Đợi câu đó xong rồi hỏi tiếp nhé."
                : "Pengoo is still answering another question of yours. Ask again once it's done.";
    }

    static String failed(String language) {
        return isVietnamese(language)
                ? "Xin lỗi, Pengoo chưa trả lời được câu này. Bạn thử hỏi lại nhé."
                : "Sorry, Pengoo couldn't answer that one. Please try again.";
    }

    static String cannotAnswer(String language) {
        return isVietnamese(language)
                ? "Pengoo không thể trả lời nội dung này."
                : "Pengoo can't help with that.";
    }

    static String emptyAnswer(String language) {
        return isVietnamese(language)
                ? "Pengoo đã xem qua nhưng chưa có gì để gợi ý thêm."
                : "Pengoo looked but has nothing to add yet.";
    }

    static String tripWelcome(String language, String tripName, String destination, long days) {
        String where = destination == null || destination.isBlank() ? tripName : destination;
        return isVietnamese(language)
                ? "Chào cả nhà! Mình là Pengoo 🐧. Chuyến " + where + " " + days
                + " ngày đã sẵn sàng. Cần gợi ý chỗ ăn, chỗ chơi hay sắp xếp lại lịch trình thì cứ @Pengoo nhé!"
                : "Hi everyone! I'm Pengoo 🐧. Your " + days + "-day trip to " + where
                + " is ready. Mention @Pengoo whenever you want food or sightseeing ideas, or help with the plan!";
    }

    static String directIntro(String language) {
        return isVietnamese(language)
                ? "Chào bạn, mình là Pengoo 🐧 — bạn đồng hành du lịch của bạn. Hỏi mình về địa điểm, món ngon, thời tiết hay chuyến đi của bạn nhé!"
                : "Hi, I'm Pengoo 🐧, your travel buddy. Ask me about places, food, weather or any of your trips!";
    }

    static List<String> directSuggestions(String language) {
        return isVietnamese(language)
                ? List.of("Gợi ý món đặc sản Đà Nẵng", "Cuối tuần này đi đâu gần Hà Nội?", "Chuyến đi sắp tới của mình có gì?")
                : List.of("What should I eat in Da Nang?", "Weekend ideas near Hanoi?", "What's on my next trip?");
    }
}
