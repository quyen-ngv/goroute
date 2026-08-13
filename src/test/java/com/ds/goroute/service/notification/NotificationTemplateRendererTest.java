package com.ds.goroute.service.notification;

import com.ds.goroute.type.NotificationType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTemplateRendererTest {

    private static final String[] SUPPORTED_LANGUAGES = {
            "en", "vi", "hi", "ja", "ko", "ru", "th", "zh-TW"
    };

    private final NotificationTemplateRenderer renderer = new NotificationTemplateRenderer();

    @Test
    void everyVietnameseNotificationBodyHasTerminalPunctuation() {
        Map<String, Object> data = completeData();

        for (NotificationType type : NotificationType.values()) {
            NotificationMessage message = renderer.render(type, data, "vi");

            assertThat(message.body())
                    .as("Vietnamese body for %s", type)
                    .matches(".*[.!?…。！？।][”\"')\\]]?$");
        }
    }

    @Test
    void scheduledSummaryIsLocalizedForEverySupportedAppLanguage() {
        Map<String, Object> data = completeData();
        Map<String, String> expectedFragments = Map.of(
                "en", "You spent",
                "vi", "Bạn đã chi",
                "hi", "आपने",
                "ja", "使いました",
                "ko", "사용했습니다",
                "ru", "Вы потратили",
                "th", "คุณใช้จ่าย",
                "zh-TW", "共花費"
        );

        expectedFragments.forEach((language, fragment) -> {
            NotificationMessage message = renderer.render(
                    NotificationType.TRIP_SUMMARY,
                    data,
                    language
            );

            assertThat(message.body()).contains(fragment).doesNotContain("{");
        });
    }

    @Test
    void existingExpenseNotificationIsLocalizedForEverySupportedAppLanguage() {
        Map<String, String> expectedTitles = Map.of(
                "en", "New expense",
                "vi", "Có chi phí mới",
                "hi", "नया खर्च जोड़ा गया",
                "ja", "支出を追加",
                "ko", "비용이 추가되었습니다",
                "ru", "Добавлен расход",
                "th", "เพิ่มค่าใช้จ่ายแล้ว",
                "zh-TW", "新增支出"
        );

        expectedTitles.forEach((language, title) -> assertThat(
                renderer.render(NotificationType.EXPENSE_ADDED, completeData(), language).title()
        ).isEqualTo(title));
    }

    @Test
    void everyNotificationIsCompleteAndCleanInEverySupportedLanguage() {
        Map<String, Object> data = completeData();

        for (String language : SUPPORTED_LANGUAGES) {
            for (NotificationType type : NotificationType.values()) {
                NotificationMessage message = renderer.render(type, data, language);

                assertThat(message.title())
                        .as("%s title for %s", language, type)
                        .isNotBlank()
                        .doesNotContain("{")
                        .doesNotContain("Ã", "Â", "â€", "�");
                assertThat(message.body())
                        .as("%s body for %s", language, type)
                        .isNotBlank()
                        .doesNotContain("{")
                        .doesNotContain("Ã", "Â", "â€", "�")
                        .matches(".*[.!?…。！？।][”\"')\\]]?$");
            }
        }
    }

    @Test
    void localizedCopyNeverFallsBackToEnglish() {
        Map<String, Object> data = completeData();

        for (NotificationType type : NotificationType.values()) {
            if (type == NotificationType.ADMIN_ANNOUNCEMENT
                    || type == NotificationType.ADMIN_MESSAGE) {
                continue;
            }
            NotificationMessage english = renderer.render(type, data, "en");
            for (String language : SUPPORTED_LANGUAGES) {
                if ("en".equals(language)) {
                    continue;
                }
                NotificationMessage localized = renderer.render(type, data, language);
                assertThat(localized)
                        .as("localized copy for %s in %s", type, language)
                        .isNotEqualTo(english);
            }
        }
    }

    private Map<String, Object> completeData() {
        Map<String, Object> data = new HashMap<>();
        data.put("title", "Thông báo");
        data.put("body", "Nội dung");
        data.put("actorName", "An");
        data.put("tripName", "Bangkok");
        data.put("expenseName", "Dinner");
        data.put("expenseDescription", "Dinner");
        data.put("amount", "100");
        data.put("currency", "THB");
        data.put("activityName", "Temple");
        data.put("memberName", "Binh");
        data.put("newMemberName", "Binh");
        data.put("removedMemberName", "Binh");
        data.put("guestName", "Guest");
        data.put("linkedUserName", "User");
        data.put("payerName", "An");
        data.put("payeeName", "Binh");
        data.put("isPaid", true);
        data.put("itemName", "Temple");
        data.put("itemKind", "place");
        data.put("preparationLeadMinutes", 120);
        data.put("nextItemName", "Museum");
        data.put("nextItemKind", "booking");
        data.put("totalExpense", "2500");
        data.put("placesCount", 4);
        data.put("outstandingAmount", "300");
        data.put("hasOutstandingDebt", true);
        data.put("payeeNames", "Binh");
        return data;
    }
}
