package com.ds.goroute.service.notification;

import com.ds.goroute.type.NotificationType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class NotificationTemplateRenderer {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_]+)}");

    private final Map<String, Map<NotificationType, NotificationMessage>> templates = Map.ofEntries(
            Map.entry("en", english()),
            Map.entry("vi", vietnamese()),
            Map.entry("hi", hindi()),
            Map.entry("ja", japanese()),
            Map.entry("ko", korean()),
            Map.entry("ru", russian()),
            Map.entry("th", thai()),
            Map.entry("zh-TW", traditionalChinese()),
            Map.entry("zh-CN", simplifiedChinese()),
            Map.entry("fr", french()),
            Map.entry("de", german()),
            Map.entry("es", spanish())
    );

    public NotificationMessage render(NotificationType type, Map<String, Object> data, String language) {
        String lang = NotificationLanguage.normalize(language);
        Map<String, Object> normalizedData = normalizeData(data, lang);
        if (isAdminNotification(type)) {
            NotificationMessage adminMessage = renderAdminMessage(normalizedData);
            if (adminMessage != null) {
                return withLocalizedPunctuation(adminMessage, lang);
            }
        }
        if (type == NotificationType.SOCIAL_PLACES_EXTRACTED) {
            return withLocalizedPunctuation(
                    renderSocialPlacesExtracted(normalizedData, lang), lang);
        }
        if (type == NotificationType.AI_TRIP_CREATED) {
            return withLocalizedPunctuation("VIDEO".equals(normalizedData.get("source"))
                    ? renderAiTripCreated(lang) : renderAiTripPlanned(lang), lang);
        }
        if (type == NotificationType.SOCIAL_LIKE || type == NotificationType.SOCIAL_COMMENT) {
            return withLocalizedPunctuation(renderSocialInteraction(type, normalizedData, lang), lang);
        }
        if (type == NotificationType.FOLLOWING_CHECKIN) {
            return withLocalizedPunctuation(renderFollowingCheckin(normalizedData, lang), lang);
        }
        if (type == NotificationType.FOLLOWING_TRIP_SHARED) {
            return withLocalizedPunctuation(renderFollowingTripShared(normalizedData, lang), lang);
        }
        if (type == NotificationType.FOLLOWING_POST) {
            return withLocalizedPunctuation(renderFollowingPost(normalizedData, lang), lang);
        }
        if (type == NotificationType.PAYMENT_REMINDER && expenseCount(normalizedData) > 1) {
            return withLocalizedPunctuation(renderGroupedPaymentReminder(normalizedData, lang), lang);
        }
        if (type == NotificationType.EXPENSE_ADDED && "true".equals(stringValue(normalizedData.get("personal")))) {
            return withLocalizedPunctuation(renderPersonalExpenseAdded(normalizedData, lang), lang);
        }

        NotificationMessage template = templates
                .getOrDefault(lang, templates.get(NotificationLanguage.DEFAULT))
                .get(type);

        if (template == null && !NotificationLanguage.DEFAULT.equals(lang)) {
            return withLocalizedPunctuation(localizedFallback(lang), lang);
        }
        if (template == null) {
            return new NotificationMessage("Trip update", "There is a new update in your trip.");
        }

        return withLocalizedPunctuation(new NotificationMessage(
                interpolate(template.title(), normalizedData),
                interpolate(template.body(), normalizedData)
        ), lang);
    }

    private NotificationMessage localizedFallback(String language) {
        return switch (language) {
            case "vi" -> new NotificationMessage("Cập nhật chuyến đi", "Có một cập nhật mới trong chuyến đi của bạn");
            case "hi" -> new NotificationMessage("यात्रा अपडेट", "आपकी यात्रा में एक नया अपडेट है");
            case "ja" -> new NotificationMessage("旅行の更新", "旅行に新しい更新があります");
            case "ko" -> new NotificationMessage("여행 업데이트", "여행에 새로운 업데이트가 있습니다");
            case "ru" -> new NotificationMessage("Обновление поездки", "В вашей поездке есть новое обновление");
            case "th" -> new NotificationMessage("อัปเดตทริป", "มีการอัปเดตใหม่ในทริปของคุณ");
            case "zh-TW" -> new NotificationMessage("行程更新", "你的行程有一則新更新");
            case "zh-CN" -> new NotificationMessage("行程更新", "你的行程有一条新动态");
            case "fr" -> new NotificationMessage("Mise à jour du voyage", "Il y a du nouveau dans votre voyage");
            case "de" -> new NotificationMessage("Reise-Update", "Es gibt eine Neuigkeit in deiner Reise");
            case "es" -> new NotificationMessage("Novedad del viaje", "Hay una novedad en tu viaje");
            default -> new NotificationMessage("Trip update", "There is a new update in your trip");
        };
    }

    private NotificationMessage withLocalizedPunctuation(NotificationMessage message, String language) {
        String punctuation = switch (language) {
            case "ja", "zh-TW", "zh-CN" -> "。";
            case "hi" -> "।";
            default -> ".";
        };
        return new NotificationMessage(
                message.title(),
                ensureTerminalPunctuation(message.body(), punctuation)
        );
    }

    private String ensureTerminalPunctuation(String text, String punctuation) {
        if (text == null || text.isBlank() || text.matches(".*[.!?…。！？।][”\"')\\]]?$")) {
            return text;
        }
        return text + punctuation;
    }

    private boolean isAdminNotification(NotificationType type) {
        return type == NotificationType.ADMIN_ANNOUNCEMENT
                || type == NotificationType.ADMIN_MESSAGE;
    }

    private NotificationMessage renderAdminMessage(Map<String, Object> data) {
        String title = stringValue(data.get("title"));
        String body = stringValue(data.get("body"));
        if (title == null && body == null) {
            return null;
        }
        return new NotificationMessage(
                title != null ? title : "OnDetour Announcement",
                body != null ? body : ""
        );
    }

    private NotificationMessage renderSocialPlacesExtracted(Map<String, Object> data, String language) {
        String placeCount = stringValue(data.get("placeCount"));
        String platformName = stringValue(data.get("platformName"));
        placeCount = placeCount == null ? "0" : placeCount;
        platformName = platformName == null ? "Social" : platformName;
        return switch (language) {
            case "vi" -> new NotificationMessage(
                    "Đã trích xuất địa điểm",
                    "Đã tìm thấy " + placeCount + " địa điểm từ " + platformName
                            + ". Mở Địa điểm đã lưu để xem kết quả");
            case "hi" -> new NotificationMessage(
                    "स्थान निकाले गए",
                    platformName + " से " + placeCount
                            + " स्थान मिले। परिणाम देखने के लिए सहेजे गए स्थान खोलें");
            case "ja" -> new NotificationMessage(
                    "スポットを抽出しました",
                    platformName + " から " + placeCount
                            + " 件のスポットが見つかりました。保存済みスポットで確認できます");
            case "ko" -> new NotificationMessage(
                    "장소 추출 완료",
                    platformName + "에서 " + placeCount
                            + "개의 장소를 찾았습니다. 저장된 장소에서 결과를 확인하세요");
            case "ru" -> new NotificationMessage(
                    "Места извлечены",
                    "Найдено мест из " + platformName + ": " + placeCount
                            + ". Откройте сохранённые места, чтобы посмотреть результат");
            case "th" -> new NotificationMessage(
                    "ดึงข้อมูลสถานที่แล้ว",
                    "พบ " + placeCount + " สถานที่จาก " + platformName
                            + " เปิดสถานที่ที่บันทึกไว้เพื่อดูผลลัพธ์");
            case "zh-TW" -> new NotificationMessage(
                    "地點擷取完成",
                    "已從 " + platformName + " 找到 " + placeCount
                            + " 個地點。開啟已儲存地點查看結果");
            case "zh-CN" -> new NotificationMessage(
                    "地点提取完成",
                    "已从 " + platformName + " 找到 " + placeCount
                            + " 个地点。打开已保存的地点查看结果");
            case "fr" -> new NotificationMessage(
                    "Lieux extraits",
                    "Lieux extraits de " + platformName + " : " + placeCount
                            + ". Ouvrez Lieux enregistrés pour voir le résultat");
            case "de" -> new NotificationMessage(
                    "Orte extrahiert",
                    "Aus " + platformName + " extrahierte Orte: " + placeCount
                            + ". Öffne Gespeicherte Orte, um das Ergebnis anzusehen");
            case "es" -> new NotificationMessage(
                    "Lugares extraídos",
                    "Lugares extraídos de " + platformName + ": " + placeCount
                            + ". Abre Lugares guardados para ver el resultado");
            default -> new NotificationMessage(
                    "Places extracted",
                    "Places extracted from " + platformName + ": " + placeCount
                            + ". Open Saved Places to review the result");
        };
    }

    private int expenseCount(Map<String, Object> data) {
        String raw = stringValue(data.get("expenseCount"));
        if (raw == null) {
            return 1;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    /**
     * One Wallet reminder covering several expenses of one person. The total is shown only
     * when they share a currency; otherwise the payload leaves {@code amount} empty.
     */
    /** An expense outside any trip that someone split with the reader: there is no trip to name. */
    private NotificationMessage renderPersonalExpenseAdded(Map<String, Object> data, String language) {
        String actor = Objects.toString(stringValue(data.get("actorName")), "Someone");
        String name = Objects.toString(stringValue(data.get("expenseName")), "");
        String share = (Objects.toString(stringValue(data.get("shareAmount")), "") + " "
                + Objects.toString(stringValue(data.get("currency")), "")).trim();
        return switch (language) {
            case "vi" -> new NotificationMessage("Có khoản chi chia với bạn",
                    actor + " đã chia “" + name + "” với bạn, phần của bạn " + share);
            case "hi" -> new NotificationMessage("आपके साथ खर्च बाँटा गया",
                    actor + " ने “" + name + "” आपके साथ बाँटा, आपका हिस्सा " + share);
            case "ja" -> new NotificationMessage("割り勘の支出",
                    actor + "さんが「" + name + "」をあなたと割り勘にしました（あなたの分 " + share + "）");
            case "ko" -> new NotificationMessage("함께 나눈 지출",
                    actor + "님이 “" + name + "”을(를) 함께 나눴습니다. 내 몫 " + share);
            case "ru" -> new NotificationMessage("Общий расход",
                    actor + " разделил(а) с вами «" + name + "», ваша доля " + share);
            case "th" -> new NotificationMessage("ค่าใช้จ่ายที่หารกับคุณ",
                    actor + " หาร “" + name + "” กับคุณ ส่วนของคุณ " + share);
            case "zh-TW" -> new NotificationMessage("與你分攤的支出",
                    actor + " 與你分攤「" + name + "」，你的份額 " + share);
            case "zh-CN" -> new NotificationMessage("与你分摊的支出",
                    actor + " 与你分摊了“" + name + "”，你的份额 " + share);
            case "fr" -> new NotificationMessage("Dépense partagée avec vous",
                    actor + " a partagé « " + name + " » avec vous, votre part est de " + share);
            case "de" -> new NotificationMessage("Ausgabe mit dir geteilt",
                    actor + " hat „" + name + "“ mit dir geteilt, dein Anteil beträgt " + share);
            case "es" -> new NotificationMessage("Gasto compartido contigo",
                    actor + " dividió “" + name + "” contigo, tu parte es " + share);
            default -> new NotificationMessage("Expense shared with you",
                    actor + " split “" + name + "” with you, your share is " + share);
        };
    }

    private NotificationMessage renderGroupedPaymentReminder(Map<String, Object> data, String language) {
        String actor = stringValue(data.get("actorName"));
        String count = String.valueOf(expenseCount(data));
        String amount = stringValue(data.get("amount"));
        String total = amount == null ? null : (amount + " " + Objects.toString(data.get("currency"), "")).trim();
        return switch (language) {
            case "vi" -> new NotificationMessage("Nhắc thanh toán", total == null
                    ? (actor == null ? "Ai đó" : actor) + " nhắc bạn thanh toán " + count + " khoản chi"
                    : (actor == null ? "Ai đó" : actor) + " nhắc bạn thanh toán " + count + " khoản chi, tổng " + total);
            case "hi" -> new NotificationMessage("भुगतान रिमाइंडर", total == null
                    ? (actor == null ? "किसी" : actor) + " ने " + count + " बकाया खर्चों के भुगतान की याद दिलाई"
                    : (actor == null ? "किसी" : actor) + " ने " + count + " बकाया खर्चों (कुल " + total + ") के भुगतान की याद दिलाई");
            case "ja" -> new NotificationMessage("支払いのリマインダー", total == null
                    ? (actor == null ? "誰か" : actor) + "さんが未払いの" + count + "件についてリマインドしました"
                    : (actor == null ? "誰か" : actor) + "さんが未払いの" + count + "件（合計" + total + "）についてリマインドしました");
            case "ko" -> new NotificationMessage("결제 알림", total == null
                    ? (actor == null ? "누군가" : actor) + "님이 미결제 " + count + "건의 결제를 알려드립니다"
                    : (actor == null ? "누군가" : actor) + "님이 미결제 " + count + "건(총 " + total + ")의 결제를 알려드립니다");
            case "ru" -> new NotificationMessage("Напоминание об оплате", total == null
                    ? (actor == null ? "Кто-то" : actor) + " напоминает о неоплаченных расходах: " + count
                    : (actor == null ? "Кто-то" : actor) + " напоминает о неоплаченных расходах: " + count + " на сумму " + total);
            case "th" -> new NotificationMessage("แจ้งเตือนการชำระเงิน", total == null
                    ? (actor == null ? "มีคน" : actor) + " เตือนคุณเรื่องค่าใช้จ่ายค้างชำระ " + count + " รายการ"
                    : (actor == null ? "มีคน" : actor) + " เตือนคุณเรื่องค่าใช้จ่ายค้างชำระ " + count + " รายการ รวม " + total);
            case "zh-TW" -> new NotificationMessage("付款提醒", total == null
                    ? (actor == null ? "有人" : actor) + " 提醒您支付 " + count + " 筆未付款項"
                    : (actor == null ? "有人" : actor) + " 提醒您支付 " + count + " 筆未付款項，共 " + total);
            case "zh-CN" -> new NotificationMessage("付款提醒", total == null
                    ? (actor == null ? "有人" : actor) + " 提醒你支付 " + count + " 笔未付款项"
                    : (actor == null ? "有人" : actor) + " 提醒你支付 " + count + " 笔未付款项，共 " + total);
            case "fr" -> new NotificationMessage("Rappel de paiement", total == null
                    ? (actor == null ? "Quelqu'un" : actor) + " vous rappelle " + count + " dépenses non réglées"
                    : (actor == null ? "Quelqu'un" : actor) + " vous rappelle " + count + " dépenses non réglées, " + total + " au total");
            case "de" -> new NotificationMessage("Zahlungserinnerung", total == null
                    ? (actor == null ? "Jemand" : actor) + " erinnert dich an " + count + " offene Ausgaben"
                    : (actor == null ? "Jemand" : actor) + " erinnert dich an " + count + " offene Ausgaben, insgesamt " + total);
            case "es" -> new NotificationMessage("Recordatorio de pago", total == null
                    ? (actor == null ? "Alguien" : actor) + " te recordó " + count + " gastos pendientes"
                    : (actor == null ? "Alguien" : actor) + " te recordó " + count + " gastos pendientes, " + total + " en total");
            default -> new NotificationMessage("Payment reminder", total == null
                    ? (actor == null ? "Someone" : actor) + " reminded you about " + count + " unpaid expenses"
                    : (actor == null ? "Someone" : actor) + " reminded you about " + count + " unpaid expenses, " + total + " in total");
        };
    }

    private NotificationMessage renderAiTripCreated(String language) {
        return switch (language) {
            case "vi" -> new NotificationMessage(
                    "\u0110\u00e3 t\u1ea1o l\u1ecbch tr\u00ecnh",
                    "\u0110\u00e3 l\u01b0u l\u1ecbch tr\u00ecnh t\u1eeb video. M\u1edf chuy\u1ebfn \u0111i \u0111\u1ec3 xem");
            case "ja" -> new NotificationMessage("\u65c5\u7a0b\u3092\u4f5c\u6210\u3057\u307e\u3057\u305f", "\u52d5\u753b\u304b\u3089\u65c5\u7a0b\u3092\u4fdd\u5b58\u3057\u307e\u3057\u305f\u3002\u65c5\u884c\u3092\u958b\u3044\u3066\u78ba\u8a8d\u3067\u304d\u307e\u3059");
            case "ko" -> new NotificationMessage("\uc77c\uc815\uc774 \uc0dd\uc131\ub418\uc5c8\uc5b4\uc694", "\ub3d9\uc601\uc0c1\uc5d0\uc11c \uc77c\uc815\uc744 \uc800\uc7a5\ud588\uc5b4\uc694. \ud2b8\ub9bd\uc744 \uc5f4\uc5b4 \ud655\uc778\ud558\uc138\uc694");
            case "hi" -> new NotificationMessage("यात्रा कार्यक्रम बनाया गया", "वीडियो से यात्रा कार्यक्रम बन गया है। देखने और संपादित करने के लिए यात्रा खोलें");
            case "ru" -> new NotificationMessage("Маршрут создан", "Маршрут из видео создан. Откройте поездку, чтобы посмотреть и изменить его");
            case "th" -> new NotificationMessage("สร้างกำหนดการเดินทางแล้ว", "สร้างกำหนดการจากวิดีโอแล้ว เปิดทริปเพื่อดูและแก้ไข");
            case "zh-TW" -> new NotificationMessage("行程已建立", "已從影片建立行程。開啟行程即可查看及編輯");
            case "zh-CN" -> new NotificationMessage("行程已生成", "已根据视频生成行程。打开行程即可查看和编辑");
            case "fr" -> new NotificationMessage("Itinéraire créé", "Votre itinéraire tiré de la vidéo est prêt. Ouvrez le voyage pour le consulter");
            case "de" -> new NotificationMessage("Reiseplan erstellt", "Dein Reiseplan aus dem Video ist fertig. Öffne die Reise, um ihn anzusehen");
            case "es" -> new NotificationMessage("Itinerario creado", "Tu itinerario a partir del vídeo está listo. Abre el viaje para revisarlo");
            default -> new NotificationMessage("Itinerary created", "Your itinerary from the video is ready. Open the trip to review it");
        };
    }

    /** An AI plan from the trip form or a chat, not from a video. */
    private NotificationMessage renderAiTripPlanned(String language) {
        return switch (language) {
            case "vi" -> new NotificationMessage("Đã tạo lịch trình", "Lịch trình AI của bạn đã sẵn sàng. Mở chuyến đi để xem và chỉnh sửa");
            case "ja" -> new NotificationMessage("旅程を作成しました", "AIの旅程ができました。旅行を開いて確認・編集できます");
            case "ko" -> new NotificationMessage("일정이 생성되었어요", "AI 여행 일정이 준비되었어요. 여행을 열어 확인하고 수정하세요");
            case "hi" -> new NotificationMessage("यात्रा कार्यक्रम बनाया गया", "आपका AI यात्रा कार्यक्रम तैयार है। देखने और संपादित करने के लिए यात्रा खोलें");
            case "ru" -> new NotificationMessage("Маршрут создан", "Ваш маршрут от ИИ готов. Откройте поездку, чтобы посмотреть и изменить его");
            case "th" -> new NotificationMessage("สร้างกำหนดการเดินทางแล้ว", "กำหนดการจาก AI พร้อมแล้ว เปิดทริปเพื่อดูและแก้ไข");
            case "zh-TW" -> new NotificationMessage("行程已建立", "AI 行程已準備好。開啟行程即可查看及編輯");
            case "zh-CN" -> new NotificationMessage("行程已生成", "你的 AI 行程已准备好。打开行程即可查看和编辑");
            case "fr" -> new NotificationMessage("Itinéraire créé", "Votre itinéraire IA est prêt. Ouvrez le voyage pour le consulter et le modifier");
            case "de" -> new NotificationMessage("Reiseplan erstellt", "Dein KI-Reiseplan ist fertig. Öffne die Reise, um ihn anzusehen und zu bearbeiten");
            case "es" -> new NotificationMessage("Itinerario creado", "Tu itinerario con IA está listo. Abre el viaje para revisarlo y editarlo");
            default -> new NotificationMessage("Itinerary created", "Your AI itinerary is ready. Open the trip to review and edit it");
        };
    }

    private NotificationMessage renderSocialInteraction(NotificationType type,
                                                         Map<String, Object> data,
                                                         String language) {
        int count = 1;
        Object rawCount = data.get("actorCount");
        if (rawCount instanceof Number number) {
            count = Math.max(1, number.intValue());
        } else if (rawCount != null) {
            try {
                count = Math.max(1, Integer.parseInt(String.valueOf(rawCount)));
            } catch (NumberFormatException ignored) {
                // Keep the single-actor fallback.
            }
        }
        String actor = stringValue(data.get("actorName"));
        String actorLabel = count > 1 ? String.valueOf(count) : (actor == null ? "Someone" : actor);
        String target = socialTargetLabel(stringValue(data.get("targetType")), language);
        boolean like = type == NotificationType.SOCIAL_LIKE;
        return switch (language) {
            case "vi" -> new NotificationMessage(like ? "Lượt thích mới" : "Bình luận mới",
                    like ? actorLabel + " đã thích " + target + " của bạn"
                            : actorLabel + " đã bình luận về " + target + " của bạn");
            case "hi" -> new NotificationMessage(like ? "नई पसंद" : "नई टिप्पणी",
                    like ? actorLabel + " ने आपके " + target + " को पसंद किया"
                            : actorLabel + " ने आपके " + target + " पर टिप्पणी की");
            case "ja" -> new NotificationMessage(like ? "新しいリアクション" : "新しいコメント",
                    like ? actorLabel + "があなたの" + target + "にリアクションしました"
                            : actorLabel + "があなたの" + target + "にコメントしました");
            case "ko" -> new NotificationMessage(like ? "새 좋아요" : "새 댓글",
                    like ? actorLabel + " 내 " + target + "을 좋아합니다"
                            : actorLabel + " 내 " + target + "에 댓글을 남겼습니다");
            case "ru" -> new NotificationMessage(like ? "Новая реакция" : "Новый комментарий",
                    like ? actorLabel + " отметили «Нравится» ваш " + target
                            : actorLabel + " прокомментировали ваш " + target);
            case "th" -> new NotificationMessage(like ? "มีคนถูกใจใหม่" : "ความคิดเห็นใหม่",
                    like ? actorLabel + " ถูกใจ" + target + "ของคุณ"
                            : actorLabel + " แสดงความคิดเห็นเกี่ยวกับ" + target + "ของคุณ");
            case "zh-TW" -> new NotificationMessage(like ? "新的讚" : "新的留言",
                    like ? actorLabel + "對你的" + target + "按了讚"
                            : actorLabel + "評論了你的" + target);
            case "zh-CN" -> new NotificationMessage(like ? "新的赞" : "新的评论",
                    like ? actorLabel + " 赞了你的" + target
                            : actorLabel + " 评论了你的" + target);
            case "fr" -> new NotificationMessage(like ? "Nouveau j'aime" : "Nouveau commentaire",
                    like ? actorLabel + " a aimé votre " + target
                            : actorLabel + " a commenté votre " + target);
            case "de" -> new NotificationMessage(like ? "Neues Gefällt mir" : "Neuer Kommentar",
                    like ? actorLabel + " hat " + target + " mit „Gefällt mir“ markiert"
                            : actorLabel + " hat " + target + " kommentiert");
            case "es" -> new NotificationMessage(like ? "Nuevo me gusta" : "Nuevo comentario",
                    like ? actorLabel + " indicó que le gusta tu " + target
                            : actorLabel + " comentó tu " + target);
            default -> new NotificationMessage(like ? "New like" : "New comment",
                    like ? actorLabel + " liked your " + target
                            : actorLabel + " commented on your " + target);
        };
    }

    private String socialTargetLabel(String raw, String language) {
        String target = raw == null ? "" : raw.trim().toUpperCase();
        return switch (language) {
            case "vi" -> switch (target) {
                case "TRIP" -> "chuyến đi";
                case "CHECKIN" -> "check-in";
                case "REVIEW" -> "đánh giá";
                case "POST" -> "bài viết";
                default -> "bình luận";
            };
            case "hi" -> switch (target) {
                case "TRIP" -> "यात्रा";
                case "CHECKIN" -> "चेक-इन";
                case "REVIEW" -> "समीक्षा";
                case "POST" -> "लेख";
                default -> "टिप्पणी";
            };
            case "ja" -> switch (target) {
                case "TRIP" -> "旅行";
                case "CHECKIN" -> "チェックイン";
                case "REVIEW" -> "レビュー";
                case "POST" -> "記事";
                default -> "コメント";
            };
            case "ko" -> switch (target) {
                case "TRIP" -> "여행";
                case "CHECKIN" -> "체크인";
                case "REVIEW" -> "리뷰";
                case "POST" -> "글";
                default -> "댓글";
            };
            case "ru" -> switch (target) {
                case "TRIP" -> "поездку";
                case "CHECKIN" -> "отметку о посещении";
                case "REVIEW" -> "отзыв";
                case "POST" -> "статью";
                default -> "комментарий";
            };
            case "th" -> switch (target) {
                case "TRIP" -> "ทริป";
                case "CHECKIN" -> "เช็กอิน";
                case "REVIEW" -> "รีวิว";
                case "POST" -> "บทความ";
                default -> "ความคิดเห็น";
            };
            case "zh-TW" -> switch (target) {
                case "TRIP" -> "行程";
                case "CHECKIN" -> "打卡";
                case "REVIEW" -> "評價";
                case "POST" -> "文章";
                default -> "留言";
            };
            case "zh-CN" -> switch (target) {
                case "TRIP" -> "行程";
                case "CHECKIN" -> "打卡";
                case "REVIEW" -> "评价";
                case "POST" -> "文章";
                default -> "评论";
            };
            case "fr" -> switch (target) {
                case "TRIP" -> "voyage";
                case "CHECKIN" -> "check-in";
                case "REVIEW" -> "avis";
                case "POST" -> "article";
                default -> "commentaire";
            };
            case "de" -> switch (target) {
                case "TRIP" -> "deine Reise";
                case "CHECKIN" -> "deinen Check-in";
                case "REVIEW" -> "deine Bewertung";
                case "POST" -> "deinen Beitrag";
                default -> "deinen Kommentar";
            };
            case "es" -> switch (target) {
                case "TRIP" -> "viaje";
                case "CHECKIN" -> "check-in";
                case "REVIEW" -> "reseña";
                case "POST" -> "artículo";
                default -> "comentario";
            };
            default -> switch (target) {
                case "TRIP" -> "trip";
                case "CHECKIN" -> "check-in";
                case "REVIEW" -> "review";
                case "POST" -> "post";
                default -> "comment";
            };
        };
    }

    /**
     * "Minh — checked in at Hồ Gươm". The title is the author, as a friend's post reads on a lock
     * screen. {@code postCount} > 1 means earlier posts were held back by the author's cooldown and
     * this push announces them together.
     */
    private NotificationMessage renderFollowingCheckin(Map<String, Object> data, String language) {
        String actor = followingActor(data, language);
        String place = stringValue(data.get("placeName"));
        int count = Math.max(1, intValue(data.get("postCount")));
        String body = switch (language) {
            case "vi" -> count > 1
                    ? "Vừa check-in " + count + " nơi" + (place == null ? "" : ", mới nhất tại " + place)
                    : place == null ? "Vừa check-in một nơi mới" : "Vừa check-in tại " + place;
            case "hi" -> count > 1
                    ? count + " जगहों पर चेक-इन किया" + (place == null ? "" : ", सबसे नया " + place + " में")
                    : place == null ? "एक नई जगह पर चेक-इन किया" : place + " में चेक-इन किया";
            case "ja" -> count > 1
                    ? count + "か所にチェックインしました" + (place == null ? "" : "（最新：" + place + "）")
                    : place == null ? "新しい場所にチェックインしました" : place + "にチェックインしました";
            case "ko" -> count > 1
                    ? count + "곳에 체크인했어요" + (place == null ? "" : " (최근: " + place + ")")
                    : place == null ? "새로운 곳에 체크인했어요" : place + "에 체크인했어요";
            case "ru" -> count > 1
                    ? "Отметился в " + count + " местах" + (place == null ? "" : ", последнее — " + place)
                    : place == null ? "Отметился в новом месте" : "Отметился: " + place;
            case "th" -> count > 1
                    ? "เพิ่งเช็กอิน " + count + " แห่ง" + (place == null ? "" : " ล่าสุดที่ " + place)
                    : place == null ? "เพิ่งเช็กอินที่ใหม่" : "เพิ่งเช็กอินที่ " + place;
            case "zh-TW" -> count > 1
                    ? "剛在 " + count + " 個地點打卡" + (place == null ? "" : "，最新在" + place)
                    : place == null ? "剛在新地點打卡" : "剛在" + place + "打卡";
            case "zh-CN" -> count > 1
                    ? "刚在 " + count + " 个地点打卡" + (place == null ? "" : "，最新在" + place)
                    : place == null ? "刚在新地点打卡" : "刚在" + place + "打卡";
            case "fr" -> count > 1
                    ? "A fait un check-in dans " + count + " lieux" + (place == null ? "" : ", le dernier à " + place)
                    : place == null ? "A fait un check-in dans un nouveau lieu" : "A fait un check-in à " + place;
            case "de" -> count > 1
                    ? "Hat an " + count + " Orten eingecheckt" + (place == null ? "" : ", zuletzt bei " + place)
                    : place == null ? "Hat an einem neuen Ort eingecheckt" : "Hat bei " + place + " eingecheckt";
            case "es" -> count > 1
                    ? "Hizo check-in en " + count + " lugares" + (place == null ? "" : ", el último en " + place)
                    : place == null ? "Hizo check-in en un lugar nuevo" : "Hizo check-in en " + place;
            default -> count > 1
                    ? "Checked in at " + count + " places" + (place == null ? "" : ", latest at " + place)
                    : place == null ? "Checked in somewhere new" : "Checked in at " + place;
        };
        return new NotificationMessage(actor, body);
    }

    private NotificationMessage renderFollowingTripShared(Map<String, Object> data, String language) {
        String actor = followingActor(data, language);
        String trip = stringValue(data.get("tripTitle"));
        String body = switch (language) {
            case "vi" -> trip == null ? "Vừa chia sẻ một lịch trình mới" : "Vừa chia sẻ lịch trình " + trip;
            case "hi" -> trip == null ? "एक नई यात्रा योजना साझा की" : "यात्रा योजना साझा की: " + trip;
            case "ja" -> trip == null ? "新しい旅程を公開しました" : "旅程「" + trip + "」を公開しました";
            case "ko" -> trip == null ? "새 여행 일정을 공유했어요" : "여행 일정 '" + trip + "'을 공유했어요";
            case "ru" -> trip == null ? "Поделился новым маршрутом" : "Поделился маршрутом «" + trip + "»";
            case "th" -> trip == null ? "เพิ่งแชร์แผนเที่ยวใหม่" : "เพิ่งแชร์แผนเที่ยว " + trip;
            case "zh-TW" -> trip == null ? "剛分享了新的行程" : "剛分享了行程「" + trip + "」";
            case "zh-CN" -> trip == null ? "刚分享了新的行程" : "刚分享了行程“" + trip + "”";
            case "fr" -> trip == null ? "A partagé un nouveau programme de voyage" : "A partagé le programme de voyage " + trip;
            case "de" -> trip == null ? "Hat einen neuen Reiseplan geteilt" : "Hat den Reiseplan " + trip + " geteilt";
            case "es" -> trip == null ? "Compartió un nuevo plan de viaje" : "Compartió el plan de viaje " + trip;
            default -> trip == null ? "Shared a new trip plan" : "Shared the trip plan " + trip;
        };
        return new NotificationMessage(actor, body);
    }

    private NotificationMessage renderFollowingPost(Map<String, Object> data, String language) {
        String actor = followingActor(data, language);
        String title = stringValue(data.get("postTitle"));
        String body = switch (language) {
            case "vi" -> title == null ? "Vừa đăng một bài viết mới" : "Vừa đăng bài viết " + title;
            case "hi" -> title == null ? "एक नया लेख प्रकाशित किया" : "लेख प्रकाशित किया: " + title;
            case "ja" -> title == null ? "新しい記事を公開しました" : "記事「" + title + "」を公開しました";
            case "ko" -> title == null ? "새 글을 올렸어요" : "글 '" + title + "'을 올렸어요";
            case "ru" -> title == null ? "Опубликовал новую статью" : "Опубликовал статью «" + title + "»";
            case "th" -> title == null ? "เพิ่งโพสต์บทความใหม่" : "เพิ่งโพสต์บทความ " + title;
            case "zh-TW" -> title == null ? "剛發表了新文章" : "剛發表了文章「" + title + "」";
            case "zh-CN" -> title == null ? "刚发布了新文章" : "刚发布了文章“" + title + "”";
            case "fr" -> title == null ? "A publié un nouvel article" : "A publié l'article " + title;
            case "de" -> title == null ? "Hat einen neuen Beitrag veröffentlicht" : "Hat den Beitrag " + title + " veröffentlicht";
            case "es" -> title == null ? "Publicó un artículo nuevo" : "Publicó el artículo " + title;
            default -> title == null ? "Published a new post" : "Published the post " + title;
        };
        return new NotificationMessage(actor, body);
    }

    private String followingActor(Map<String, Object> data, String language) {
        String actor = stringValue(data.get("actorName"));
        if (actor != null) {
            return actor;
        }
        return switch (language) {
            case "vi" -> "Người bạn theo dõi";
            case "hi" -> "जिसे आप फ़ॉलो करते हैं";
            case "ja" -> "フォロー中の人";
            case "ko" -> "팔로우 중인 사람";
            case "ru" -> "Ваша подписка";
            case "th" -> "คนที่คุณติดตาม";
            case "zh-TW" -> "你追蹤的人";
            case "zh-CN" -> "你关注的人";
            case "fr" -> "Une personne que vous suivez";
            case "de" -> "Jemand, dem du folgst";
            case "es" -> "Alguien a quien sigues";
            default -> "Someone you follow";
        };
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Map<String, Object> normalizeData(Map<String, Object> data, String language) {
        Map<String, Object> normalized = new HashMap<>();
        if (data != null) {
            normalized.putAll(data);
        }
        if ("target".equals(normalized.get("recipientContext"))) {
            String pronoun = targetPronoun(language);
            if (normalized.containsKey("removedMemberName")) {
                normalized.put("removedMemberName", pronoun);
            }
            if (normalized.containsKey("linkedUserName")) {
                normalized.put("linkedUserName", pronoun);
            }
            if (normalized.containsKey("memberName")) {
                normalized.put("memberName", pronoun);
            }
        }
        Object isPaid = normalized.get("isPaid");
        if (isPaid instanceof Boolean paid) {
            normalized.put("paidStatus", paidStatus(paid, language));
        } else if (isPaid != null) {
            normalized.put("paidStatus", isPaid);
        }
        normalized.put(
                "itemKindLabel",
                itemKindLabel(stringValue(normalized.get("itemKind")), language)
        );
        normalized.put(
                "nextItemSentence",
                nextItemSentence(
                        stringValue(normalized.get("nextItemName")),
                        stringValue(normalized.get("nextItemKind")),
                        language
                )
        );
        normalized.put(
                "debtSummary",
                debtSummary(normalized, language)
        );
        normalized.put(
                "preparationLeadLabel",
                preparationLeadLabel(normalized.get("preparationLeadMinutes"), language)
        );
        normalized.put(
                "preparationChecklist",
                preparationChecklist(stringValue(normalized.get("itemKind")), language)
        );
        // The forecast is worded in English and Vietnamese only (as on the weather sheet).
        String weather = stringValue(normalized.get("weather"));
        String weatherVi = stringValue(normalized.get("weatherVi"));
        if (weather != null || weatherVi != null) {
            normalized.put("weatherLabel", "vi".equals(language)
                    ? (weatherVi != null ? weatherVi : weather)
                    : (weather != null ? weather : weatherVi));
        }
        return normalized;
    }

    private String targetPronoun(String language) {
        return switch (language) {
            case "vi" -> "bạn";
            case "hi" -> "आप";
            case "ja" -> "あなた";
            case "ko" -> "회원님";
            case "ru" -> "вас";
            case "th" -> "คุณ";
            case "zh-TW" -> "你";
            case "zh-CN" -> "你";
            case "fr" -> "vous";
            case "de" -> "dich";
            case "es" -> "ti";
            default -> "you";
        };
    }

    private String preparationLeadLabel(Object value, String language) {
        long minutes;
        try {
            minutes = value instanceof Number number
                    ? number.longValue()
                    : Long.parseLong(String.valueOf(value));
        } catch (RuntimeException exception) {
            minutes = 30;
        }
        boolean hours = minutes >= 60 && minutes % 60 == 0;
        long amount = hours ? minutes / 60 : minutes;
        return switch (language) {
            case "vi" -> amount + (hours ? " giờ" : " phút");
            case "hi" -> amount + (hours ? " घंटे" : " मिनट");
            case "ja" -> amount + (hours ? "時間" : "分");
            case "ko" -> amount + (hours ? "시간" : "분");
            case "ru" -> amount + (hours ? " ч" : " мин");
            case "th" -> amount + (hours ? " ชั่วโมง" : " นาที");
            case "zh-TW" -> amount + (hours ? " 小時" : " 分鐘");
            case "zh-CN" -> amount + (hours ? " 小时" : " 分钟");
            case "fr" -> amount + (hours ? (amount == 1 ? " heure" : " heures") : " minutes");
            case "de" -> amount + (hours ? (amount == 1 ? " Stunde" : " Stunden") : " Minuten");
            case "es" -> amount + (hours ? (amount == 1 ? " hora" : " horas") : " minutos");
            default -> amount + (hours ? (amount == 1 ? " hour" : " hours") : " minutes");
        };
    }

    private String preparationChecklist(String kind, String language) {
        boolean booking = "booking".equals(kind);
        return switch (language) {
            case "vi" -> booking
                    ? "Kiểm tra vé hoặc mã QR, điểm tập trung và lưu ý của tour nhé"
                    : "Kiểm tra giờ khởi hành, điểm đón và lộ trình nhé";
            case "hi" -> booking
                    ? "कृपया टिकट या QR कोड, मिलने की जगह और टूर से जुड़ी जानकारी जाँच लें"
                    : "कृपया प्रस्थान का समय, पिकअप की जगह और रास्ता जाँच लें";
            case "ja" -> booking
                    ? "チケットやQRコード、集合場所、ツアーの注意事項を確認しておきましょう"
                    : "出発時刻、乗車場所、ルートを確認しておきましょう";
            case "ko" -> booking
                    ? "티켓 또는 QR 코드, 미팅 장소와 투어 안내를 미리 확인해 주세요"
                    : "출발 시간, 탑승 장소와 경로를 미리 확인해 주세요";
            case "ru" -> booking
                    ? "Проверьте билет или QR-код, место встречи и требования тура"
                    : "Проверьте время отправления, место посадки и маршрут";
            case "th" -> booking
                    ? "อย่าลืมตรวจสอบตั๋วหรือคิวอาร์โค้ด จุดนัดพบ และข้อมูลสำคัญของทัวร์"
                    : "อย่าลืมตรวจสอบเวลาออกเดินทาง จุดรับ และเส้นทาง";
            case "zh-TW" -> booking
                    ? "別忘了確認票券或 QR Code、集合地點及行程須知"
                    : "別忘了確認出發時間、上車地點及路線";
            case "zh-CN" -> booking
                    ? "别忘了确认门票或二维码、集合地点和行程须知"
                    : "别忘了确认出发时间、上车地点和路线";
            case "fr" -> booking
                    ? "Vérifiez votre billet ou QR code, le point de rendez-vous et les consignes de la visite"
                    : "Vérifiez l'heure de départ, le point de prise en charge et l'itinéraire";
            case "de" -> booking
                    ? "Prüfe dein Ticket oder deinen QR-Code, den Treffpunkt und die Hinweise zur Tour"
                    : "Prüfe Abfahrtszeit, Abholort und Route";
            case "es" -> booking
                    ? "Revisa tu entrada o código QR, el punto de encuentro y las indicaciones del tour"
                    : "Revisa la hora de salida, el punto de recogida y la ruta";
            default -> booking
                    ? "Check your ticket or QR code, meeting point, and tour instructions"
                    : "Check the departure time, pickup point, and route";
        };
    }

    private String paidStatus(boolean paid, String language) {
        return switch (language) {
            case "vi" -> paid ? "đã thanh toán" : "chưa thanh toán";
            case "hi" -> paid ? "भुगतान किया गया" : "भुगतान बाकी";
            case "ja" -> paid ? "支払い済み" : "未払い";
            case "ko" -> paid ? "결제 완료" : "미결제";
            case "ru" -> paid ? "оплачено" : "не оплачено";
            case "th" -> paid ? "ชำระแล้ว" : "ยังไม่ชำระ";
            case "zh-TW" -> paid ? "已付款" : "未付款";
            case "zh-CN" -> paid ? "已付款" : "未付款";
            case "fr" -> paid ? "payé" : "non payé";
            case "de" -> paid ? "bezahlt" : "unbezahlt";
            case "es" -> paid ? "pagado" : "pendiente";
            default -> paid ? "paid" : "unpaid";
        };
    }

    private String itemKindLabel(String kind, String language) {
        String normalizedKind = kind != null ? kind : "activity";
        return switch (language) {
            case "vi" -> switch (normalizedKind) {
                case "booking" -> "tour hoặc vé";
                case "transport" -> "chặng di chuyển";
                case "place" -> "địa điểm";
                default -> "hoạt động";
            };
            case "hi" -> switch (normalizedKind) {
                case "booking" -> "टूर या टिकट";
                case "transport" -> "यात्रा चरण";
                case "place" -> "स्थान";
                default -> "गतिविधि";
            };
            case "ja" -> switch (normalizedKind) {
                case "booking" -> "ツアー・チケット";
                case "transport" -> "移動";
                case "place" -> "場所";
                default -> "アクティビティ";
            };
            case "ko" -> switch (normalizedKind) {
                case "booking" -> "투어 또는 티켓";
                case "transport" -> "이동";
                case "place" -> "장소";
                default -> "활동";
            };
            case "ru" -> switch (normalizedKind) {
                case "booking" -> "тур или билет";
                case "transport" -> "трансфер";
                case "place" -> "место";
                default -> "активность";
            };
            case "th" -> switch (normalizedKind) {
                case "booking" -> "ทัวร์หรือตั๋ว";
                case "transport" -> "การเดินทาง";
                case "place" -> "สถานที่";
                default -> "กิจกรรม";
            };
            case "zh-TW" -> switch (normalizedKind) {
                case "booking" -> "行程或票券";
                case "transport" -> "交通行程";
                case "place" -> "地點";
                default -> "活動";
            };
            case "zh-CN" -> switch (normalizedKind) {
                case "booking" -> "行程或门票";
                case "transport" -> "交通";
                case "place" -> "地点";
                default -> "活动";
            };
            case "fr" -> switch (normalizedKind) {
                case "booking" -> "visite ou billet";
                case "transport" -> "trajet";
                case "place" -> "lieu";
                default -> "activité";
            };
            case "de" -> switch (normalizedKind) {
                case "booking" -> "Tour oder Ticket";
                case "transport" -> "Fahrt";
                case "place" -> "Ort";
                default -> "Aktivität";
            };
            case "es" -> switch (normalizedKind) {
                case "booking" -> "tour o entrada";
                case "transport" -> "trayecto";
                case "place" -> "lugar";
                default -> "actividad";
            };
            default -> switch (normalizedKind) {
                case "booking" -> "tour or ticket";
                case "transport" -> "transport";
                default -> normalizedKind;
            };
        };
    }

    private String nextItemSentence(String nextItemName, String nextItemKind, String language) {
        if (nextItemName == null) {
            return "";
        }
        String kind = itemKindLabel(nextItemKind, language);
        return switch (language) {
            case "vi" -> "Tiếp theo: " + kind + " “" + nextItemName + "”";
            case "hi" -> "इसके बाद: " + kind + " “" + nextItemName + "”";
            case "ja" -> "次は" + kind + "「" + nextItemName + "」です";
            case "ko" -> "다음 일정: " + kind + " ‘" + nextItemName + "’";
            case "ru" -> "Далее: " + kind + " «" + nextItemName + "»";
            case "th" -> "ต่อไป: " + kind + " “" + nextItemName + "”";
            case "zh-TW" -> "接下來：" + kind + "「" + nextItemName + "」";
            case "zh-CN" -> "接下来：" + kind + "“" + nextItemName + "”";
            case "fr" -> "Ensuite : " + kind + " « " + nextItemName + " »";
            case "de" -> "Als Nächstes: " + kind + " „" + nextItemName + "“";
            case "es" -> "A continuación: " + kind + " “" + nextItemName + "”";
            default -> "Next up: " + kind + " “" + nextItemName + "”";
        };
    }

    private String debtSummary(Map<String, Object> data, String language) {
        boolean hasDebt = Boolean.TRUE.equals(data.get("hasOutstandingDebt"));
        if (!hasDebt) {
            return switch (language) {
                case "vi" -> "Bạn đã thanh toán hết các khoản cần trả";
                case "hi" -> "आपका कोई बकाया नहीं है";
                case "ja" -> "未精算の支払いはありません";
                case "ko" -> "남은 미정산 금액이 없습니다";
                case "ru" -> "У вас нет непогашенных долгов";
                case "th" -> "คุณไม่มียอดค้างชำระ";
                case "zh-TW" -> "所有共同支出都已結清";
                case "zh-CN" -> "所有共同支出都已结清";
                case "fr" -> "Toutes les dépenses partagées sont réglées";
                case "de" -> "Alle geteilten Ausgaben sind beglichen";
                case "es" -> "Todos los gastos compartidos están saldados";
                default -> "All shared expenses are settled";
            };
        }
        String amount = stringValue(data.get("outstandingAmount"));
        String currency = stringValue(data.get("currency"));
        String payees = stringValue(data.get("payeeNames"));
        String value = ((amount != null ? amount : "0") + " " + (currency != null ? currency : "")).trim();
        String names = payees != null ? payees : "";
        if (names.isBlank()) {
            return switch (language) {
                case "vi" -> "Bạn còn " + value + " chưa thanh toán";
                case "hi" -> "आपका " + value + " भुगतान बाकी है";
                case "ja" -> "未精算額は" + value + "です";
                case "ko" -> "남은 미정산 금액은 " + value + "입니다";
                case "ru" -> "Непогашенный долг: " + value;
                case "th" -> "คุณมียอดค้างชำระ " + value;
                case "zh-TW" -> "你尚有 " + value + " 未結清";
                case "zh-CN" -> "你还有 " + value + " 未结清";
                case "fr" -> "Il vous reste " + value + " à régler";
                case "de" -> "Du musst noch " + value + " begleichen";
                case "es" -> "Aún tienes " + value + " por saldar";
                default -> "You still have " + value + " to settle";
            };
        }
        return switch (language) {
            case "vi" -> "Bạn còn nợ " + names + ": " + value;
            case "hi" -> "आपको " + names + " को " + value + " देना है";
            case "ja" -> names + "への未精算額は" + value + "です";
            case "ko" -> names + "에게 지불할 금액은 " + value + "입니다";
            case "ru" -> "Вы должны " + names + ": " + value;
            case "th" -> "คุณยังค้างชำระ " + names + " จำนวน " + value;
            case "zh-TW" -> "你尚欠 " + names + "：" + value;
            case "zh-CN" -> "你还欠 " + names + "：" + value;
            case "fr" -> "Vous devez à " + names + " : " + value;
            case "de" -> "Du schuldest " + names + ": " + value;
            case "es" -> "Le debes a " + names + ": " + value;
            default -> "You owe " + names + ": " + value;
        };
    }

    private String interpolate(String template, Map<String, Object> data) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            Object value = data != null ? data.get(matcher.group(1)) : null;
            matcher.appendReplacement(result, Matcher.quoteReplacement(value != null ? String.valueOf(value) : ""));
        }
        matcher.appendTail(result);
        return result.toString().replaceAll("\\s+", " ").trim();
    }

    private Map<NotificationType, NotificationMessage> english() {
        return Map.ofEntries(
                entry(NotificationType.EXPENSE_ADDED, "New expense", "{actorName} added “{expenseName}” ({amount} {currency}) to “{tripName}”"),
                entry(NotificationType.EXPENSE_UPDATED, "Expense updated", "{actorName} updated “{expenseName}” in “{tripName}”"),
                entry(NotificationType.EXPENSE_DELETED, "Expense removed", "{actorName} removed “{expenseName}” from “{tripName}”"),
                entry(NotificationType.ACTIVITY_ADDED, "New activity", "{actorName} added “{activityName}” to “{tripName}”"),
                entry(NotificationType.ACTIVITY_UPDATED, "Activity updated", "{actorName} updated “{activityName}” in “{tripName}”"),
                entry(NotificationType.ACTIVITY_DELETED, "Activity removed", "{actorName} removed “{activityName}” from “{tripName}”"),
                entry(NotificationType.MEMBER_ADDED, "New trip member", "{actorName} added {newMemberName} to “{tripName}”"),
                entry(NotificationType.MEMBER_REMOVED, "Member removed", "{actorName} removed {removedMemberName} from “{tripName}”"),
                entry(NotificationType.MEMBER_ACCEPTED, "Member joined", "{memberName} joined “{tripName}”"),
                entry(NotificationType.MEMBER_JOINED, "Member joined", "{memberName} joined “{tripName}”"),
                entry(NotificationType.MEMBER_LEFT, "Member left", "{actorName} left “{tripName}”"),
                entry(NotificationType.GUEST_LINKED, "Guest profile linked", "{guestName} is now linked to {linkedUserName} in “{tripName}”"),
                entry(NotificationType.TRIP_UPDATED, "Trip updated", "{actorName} updated “{tripName}”"),
                entry(NotificationType.TRIP_DELETED, "Trip deleted", "{actorName} deleted “{tripName}”"),
                entry(NotificationType.PAYMENT_MARKED, "Payment updated", "{payerName} updated {payeeName}'s {amount} {currency} payment for “{expenseDescription}”"),
                entry(NotificationType.PAYMENT_REMINDER, "Payment reminder", "{actorName} reminded you about {amount} {currency} for “{expenseDescription}”."),
                entry(NotificationType.SETTLEMENT_ADDED, "Payment recorded", "{payerName} paid {payeeName} {amount} {currency}."),
                entry(NotificationType.TODO_ASSIGNED, "Task assigned to you", "{actorName} assigned you “{todoText}” in “{tripName}”"),
                entry(NotificationType.PAYMENT_ALL_MARKED, "Expense payments updated", "{actorName} marked payments for “{expenseDescription}” as {paidStatus}"),
                entry(NotificationType.PAYMENT_TRIP_MARKED, "Trip payments updated", "{actorName} marked payments in “{tripName}” as {paidStatus}"),
                entry(NotificationType.CHECKIN, "Checked in", "{actorName} checked in at “{activityName}” during “{tripName}”"),
                entry(NotificationType.NOTE_ADDED, "Note added", "{actorName} added a note to “{activityName}” in “{tripName}”"),
                entry(NotificationType.NOTE_DELETED, "Note removed", "{actorName} removed a note from “{activityName}” in “{tripName}”"),
                entry(NotificationType.COMMENT_ADDED, "New comment", "{actorName} commented on “{activityName}” in “{tripName}”"),
                entry(NotificationType.COMMENT_DELETED, "Comment removed", "{actorName} removed a comment from “{activityName}” in “{tripName}”"),
                entry(NotificationType.TRIP_INVITE, "You're invited", "You have been invited to join “{tripName}”"),
                entry(NotificationType.TRIP_INVITE_DECLINED, "Invitation declined", "{memberName} declined the invitation to “{tripName}”"),
                entry(NotificationType.TRIP_INVITE_CANCELLED, "Invitation cancelled", "Your invitation to “{tripName}” was cancelled"),
                entry(NotificationType.MEMBER_INVITED, "Member invited", "{actorName} invited {newMemberName} to “{tripName}”"),
                entry(NotificationType.MEMBER_JOIN_REQUESTED, "Join request", "{memberName} asked to join “{tripName}”"),
                entry(NotificationType.MEETUP_APPROVED, "Meetup approved", "“{meetupTitle}” is now on the map"),
                entry(NotificationType.MEETUP_REJECTED, "Meetup not approved", "“{meetupTitle}” was not approved. {reason}"),
                entry(NotificationType.MEETUP_REMOVED, "Meetup removed", "“{meetupTitle}” was removed from the map. {reason}"),
                entry(NotificationType.MEETUP_JOIN_REQUESTED, "New join request", "{userName} asked to join “{meetupTitle}”"),
                entry(NotificationType.MEETUP_PARTICIPANT_JOINED, "Someone joined", "{userName} joined “{meetupTitle}”"),
                entry(NotificationType.MEETUP_REQUEST_ACCEPTED, "You are in", "{hostName} accepted your request to join “{meetupTitle}”"),
                entry(NotificationType.MEETUP_REQUEST_DECLINED, "Request not accepted", "{hostName} could not accept your request to join “{meetupTitle}”"),
                entry(NotificationType.MEETUP_CANCELLED, "Meetup cancelled", "“{meetupTitle}” by {hostName} was cancelled. {reason}"),
                entry(NotificationType.MEETUP_INVITED, "You are invited", "{hostName} invited you to “{meetupTitle}”"),
                entry(NotificationType.LOCAL_EVENT_TOMORROW, "Event tomorrow", "“{eventTitle}” is tomorrow. Tap to see the time and place"),
                entry(NotificationType.LOCAL_EVENT_STARTING_SOON, "Starting soon", "“{eventTitle}” starts at {startTime}"),
                entry(NotificationType.LOCAL_EVENT_OPENING_TODAY, "Opens today", "“{eventTitle}” opens today"),
                entry(NotificationType.LOCAL_EVENT_ENDING_SOON, "Ending soon", "“{eventTitle}” closes on {endDate}. You have not checked in yet"),
                entry(NotificationType.LOCAL_EVENT_CANCELLED, "Event cancelled", "“{eventTitle}” was cancelled"),
                entry(NotificationType.LOCAL_EVENT_POSTPONED, "Event postponed", "“{eventTitle}” was postponed. There is no new date yet"),
                entry(NotificationType.LOCAL_EVENT_RESCHEDULED, "Event rescheduled", "“{eventTitle}” moved to {startDate}"),
                entry(NotificationType.MAP_LIMIT_REACHED, "Map search limit reached", "You have used today's map searches. They come back at {resetTime}"),
                entry(NotificationType.MAP_SPAM_ALERT, "Possible map spam", "{userName} was refused {rejectedCount} map requests today ({operation})"),
                entry(NotificationType.MAP_BUDGET_WARNING, "Map budget warning", "Mapbox {api} has used {usedPercent}% of this month's cap"),
                entry(NotificationType.MAP_BUDGET_REACHED, "Map budget reached", "Mapbox {api} reached its cap; searches use saved results until {resetTime}"),
                entry(NotificationType.PLACE_EDIT_APPROVED, "Place updated", "Thank you! The information about {placeName} has been updated"),
                entry(NotificationType.PLACE_EDIT_REJECTED, "Suggestion not applied", "Your suggestion for {placeName} was not applied"),
                entry(NotificationType.MEMBER_ACCESS_GRANTED, "Request accepted", "Your request to join “{tripName}” was accepted"),
                entry(NotificationType.MEMBER_JOIN_REJECTED, "Request declined", "Your request to join “{tripName}” was declined"),
                entry(NotificationType.MEMBER_ROLE_UPDATED, "Member role updated", "{actorName} changed {memberName}'s role in “{tripName}” to {role}"),
                entry(NotificationType.GUEST_UPDATED, "Guest updated", "{actorName} renamed {previousGuestName} to {guestName} in “{tripName}”"),
                entry(NotificationType.CHECKIN_UPDATED, "Check-in updated", "{actorName} updated a check-in at “{activityName}” in “{tripName}”"),
                entry(NotificationType.NOTE_UPDATED, "Note updated", "{actorName} updated a note for “{activityName}” in “{tripName}”"),
                entry(NotificationType.MEMORY_ADDED, "New trip memory", "{actorName} added a memory to “{tripName}”"),
                entry(NotificationType.MEMORY_DELETED, "Trip memory removed", "{actorName} removed a memory from “{tripName}”"),
                entry(NotificationType.TRIP_BOOK_UPDATED, "Travel Book updated", "{actorName} updated the Travel Book for “{tripName}”"),
                entry(NotificationType.TRIP_CLONED, "Trip copied", "{actorName} copied your trip “{tripName}”"),
                entry(NotificationType.ROUTE_OPTIMIZED, "Route ready", "The route for “{tripName}” has been optimized"),
                entry(NotificationType.TRIP_REMINDER, "Trip coming up", "“{tripName}” is coming up. Take a moment to review your itinerary"),
                entry(NotificationType.TRIP_STARTS_IN_ONE_WEEK, "One week to go", "“{tripName}” starts in one week. Review your itinerary and reservations when you have a moment"),
                entry(NotificationType.TRIP_STARTS_IN_THREE_DAYS, "Three days to go", "“{tripName}” starts in three days. Check your tickets, transport, and reservations"),
                entry(NotificationType.TRIP_STARTS_IN_TWO_DAYS, "Two days to go", "“{tripName}” starts in two days. Save anything you may need while offline"),
                entry(NotificationType.TRIP_STARTS_IN_ONE_DAY, "Your trip starts tomorrow", "“{tripName}” starts tomorrow. Give your itinerary and essentials one last check"),
                entry(NotificationType.TRIP_STARTS_IN_TWO_HOURS, "Leaving in two hours", "“{tripName}” starts in two hours. Check your first stop and allow enough travel time"),
                entry(NotificationType.TRIP_STARTED, "Your trip starts now", "“{tripName}” is underway. Your itinerary is ready—have a wonderful trip!"),
                entry(NotificationType.ITINERARY_ITEM_PREPARATION, "Get ready for your {itemKindLabel}", "“{itemName}” starts in {preparationLeadLabel}. {preparationChecklist}"),
                entry(NotificationType.ITINERARY_ITEM_UPCOMING, "{itemKindLabel} in 15 minutes", "“{itemName}” is next and starts in 15 minutes"),
                entry(NotificationType.ITINERARY_ITEM_COMPLETED, "How was “{itemName}”?", "You have finished “{itemName}”. Share a quick review to remember the experience. {nextItemSentence}"),
                entry(NotificationType.TRIP_DAY_ENDED, "Day {dayNumber} is a wrap", "That’s day {dayNumber} of “{tripName}” done. Check in at the places you visited and leave a quick review while it’s fresh"),
                entry(NotificationType.TRIP_DAY_WEATHER_ALERT, "Tomorrow's forecast: {weatherLabel}", "Day {dayNumber} of “{tripName}” may be affected. Review the plan in case you want to change it"),
                entry(NotificationType.TRIP_ENDED, "Welcome back", "“{tripName}” has ended. We hope you had a wonderful trip!"),
                entry(NotificationType.TRIP_SUMMARY, "Your trip at a glance", "You spent {totalExpense} {currency} across {placesCount} places. {debtSummary}"),
                entry(NotificationType.MARKETPLACE_BOOKING_REQUEST, "New booking request", "A guest sent a booking request. Please review it when you can"),
                entry(NotificationType.MARKETPLACE_BOOKING_CONFIRMED, "Booking confirmed", "Your booking is confirmed. You're all set!"),
                entry(NotificationType.MARKETPLACE_BOOKING_DECLINED, "Booking not confirmed", "Your booking request could not be confirmed. Open the app to review your options"),
                entry(NotificationType.MARKETPLACE_BOOKING_EXPIRED, "Booking request expired", "The partner did not respond in time, so your request was released. You can book again or pick another option"),
                entry(NotificationType.MARKETPLACE_BOOKING_CANCELLED_BY_GUEST, "Guest cancelled a booking", "A guest cancelled {bookingCode}. The inventory has been released"),
                entry(NotificationType.MARKETPLACE_BOOKING_UPDATED, "Booking updated", "{bookingCode} is now {statusLabel}. Open My bookings for details"),
                entry(NotificationType.MARKETPLACE_CHANGE_REQUESTED, "Change request", "A guest asked to change {bookingCode}. Review it before the request expires"),
                entry(NotificationType.MARKETPLACE_CHANGE_ANSWERED, "Change request {decisionLabel}", "Your change request for {bookingCode} was {decisionLabel}. Open the booking for the details"),
                entry(NotificationType.MARKETPLACE_REVIEW_INVITE, "How was your stay?", "Your booking {bookingCode} is complete. Share a review to help other travellers"),
                entry(NotificationType.MARKETPLACE_MESSAGE, "New message", "{senderName} sent you a message"),
                entry(NotificationType.CHAT_MENTION, "You were mentioned", "{senderName} mentioned you in {conversationTitle}"),
                entry(NotificationType.PARTNER_VERIFICATION_DECIDED, "Verification {decisionLabel}", "Your organization {organizationName} was {decisionLabel}. {reason}"),
                entry(NotificationType.PARTNER_VERIFICATION_SUBMITTED, "Partner verification submitted", "{organizationName} submitted documents for verification"),
                entry(NotificationType.PARTNER_STATEMENT_READY, "Statement ready", "Your statement for {periodLabel} is ready: {netAmount} after commission. Open Finance to review it"),
                entry(NotificationType.ADMIN_ANNOUNCEMENT, "OnDetour Announcement", "{body}"),
                entry(NotificationType.ADMIN_MESSAGE, "Message from OnDetour", "{body}")
        );
    }

    private Map<NotificationType, NotificationMessage> vietnamese() {
        return Map.ofEntries(
                entry(NotificationType.EXPENSE_ADDED, "Có chi phí mới", "{actorName} đã thêm “{expenseName}” ({amount} {currency}) vào “{tripName}”"),
                entry(NotificationType.EXPENSE_UPDATED, "Chi phí đã thay đổi", "{actorName} đã cập nhật “{expenseName}” trong “{tripName}”"),
                entry(NotificationType.EXPENSE_DELETED, "Đã gỡ chi phí", "{actorName} đã gỡ “{expenseName}” khỏi “{tripName}”"),
                entry(NotificationType.ACTIVITY_ADDED, "Có hoạt động mới", "{actorName} đã thêm “{activityName}” vào “{tripName}”"),
                entry(NotificationType.ACTIVITY_UPDATED, "Hoạt động đã thay đổi", "{actorName} đã cập nhật “{activityName}” trong “{tripName}”"),
                entry(NotificationType.ACTIVITY_DELETED, "Đã gỡ hoạt động", "{actorName} đã gỡ “{activityName}” khỏi “{tripName}”"),
                entry(NotificationType.MEMBER_ADDED, "Có thành viên mới", "{actorName} đã thêm {newMemberName} vào “{tripName}”"),
                entry(NotificationType.MEMBER_REMOVED, "Đã gỡ thành viên", "{actorName} đã gỡ {removedMemberName} khỏi “{tripName}”"),
                entry(NotificationType.MEMBER_ACCEPTED, "Thành viên đã tham gia", "{memberName} đã tham gia “{tripName}”"),
                entry(NotificationType.MEMBER_JOINED, "Thành viên đã tham gia", "{memberName} đã tham gia “{tripName}”"),
                entry(NotificationType.MEMBER_LEFT, "Thành viên đã rời chuyến đi", "{actorName} đã rời khỏi “{tripName}”"),
                entry(NotificationType.GUEST_LINKED, "Đã liên kết hồ sơ khách", "{guestName} đã được liên kết với {linkedUserName} trong “{tripName}”"),
                entry(NotificationType.TRIP_UPDATED, "Chuyến đi đã thay đổi", "{actorName} đã cập nhật “{tripName}”"),
                entry(NotificationType.TRIP_DELETED, "Đã xóa chuyến đi", "{actorName} đã xóa “{tripName}”"),
                entry(NotificationType.PAYMENT_MARKED, "Thanh toán đã thay đổi", "{payerName} đã cập nhật khoản thanh toán {amount} {currency} của {payeeName} cho “{expenseDescription}”"),
                entry(NotificationType.PAYMENT_REMINDER, "Nhắc thanh toán", "{actorName} nhắc bạn về khoản {amount} {currency} cho “{expenseDescription}”."),
                entry(NotificationType.SETTLEMENT_ADDED, "Đã ghi nhận thanh toán", "{payerName} đã trả {payeeName} {amount} {currency}."),
                entry(NotificationType.TODO_ASSIGNED, "Bạn được giao việc", "{actorName} đã giao cho bạn “{todoText}” trong “{tripName}”"),
                entry(NotificationType.PAYMENT_ALL_MARKED, "Thanh toán chi phí đã thay đổi", "{actorName} đã đánh dấu các khoản thanh toán của “{expenseDescription}” là {paidStatus}"),
                entry(NotificationType.PAYMENT_TRIP_MARKED, "Thanh toán chuyến đi đã thay đổi", "{actorName} đã đánh dấu các khoản thanh toán trong “{tripName}” là {paidStatus}"),
                entry(NotificationType.CHECKIN, "Đã check-in", "{actorName} đã check-in tại “{activityName}” trong “{tripName}”"),
                entry(NotificationType.NOTE_ADDED, "Có ghi chú mới", "{actorName} đã thêm ghi chú vào “{activityName}” trong “{tripName}”"),
                entry(NotificationType.NOTE_DELETED, "Đã gỡ ghi chú", "{actorName} đã gỡ ghi chú khỏi “{activityName}” trong “{tripName}”"),
                entry(NotificationType.COMMENT_ADDED, "Có bình luận mới", "{actorName} đã bình luận về “{activityName}” trong “{tripName}”"),
                entry(NotificationType.COMMENT_DELETED, "Đã gỡ bình luận", "{actorName} đã gỡ một bình luận khỏi “{activityName}” trong “{tripName}”"),
                entry(NotificationType.TRIP_INVITE, "Bạn có lời mời mới", "Bạn được mời tham gia “{tripName}”"),
                entry(NotificationType.TRIP_INVITE_DECLINED, "Lời mời đã bị từ chối", "{memberName} đã từ chối lời mời tham gia “{tripName}”"),
                entry(NotificationType.TRIP_INVITE_CANCELLED, "Lời mời đã bị hủy", "Lời mời tham gia “{tripName}” của bạn đã bị hủy"),
                entry(NotificationType.MEMBER_INVITED, "Đã mời thành viên", "{actorName} đã mời {newMemberName} tham gia “{tripName}”"),
                entry(NotificationType.MEMBER_JOIN_REQUESTED, "Có yêu cầu tham gia", "{memberName} muốn tham gia “{tripName}”"),
                entry(NotificationType.MEETUP_APPROVED, "Hoạt động đã được duyệt", "“{meetupTitle}” đã hiện trên bản đồ"),
                entry(NotificationType.MEETUP_REJECTED, "Hoạt động chưa được duyệt", "“{meetupTitle}” chưa được duyệt. {reason}"),
                entry(NotificationType.MEETUP_REMOVED, "Hoạt động đã bị gỡ", "“{meetupTitle}” đã bị gỡ khỏi bản đồ. {reason}"),
                entry(NotificationType.MEETUP_JOIN_REQUESTED, "Có yêu cầu tham gia mới", "{userName} muốn tham gia “{meetupTitle}”"),
                entry(NotificationType.MEETUP_PARTICIPANT_JOINED, "Có người tham gia", "{userName} đã tham gia “{meetupTitle}”"),
                entry(NotificationType.MEETUP_REQUEST_ACCEPTED, "Yêu cầu đã được chấp nhận", "{hostName} đã đồng ý cho bạn tham gia “{meetupTitle}”"),
                entry(NotificationType.MEETUP_REQUEST_DECLINED, "Yêu cầu chưa được chấp nhận", "{hostName} chưa thể nhận bạn vào “{meetupTitle}”"),
                entry(NotificationType.MEETUP_CANCELLED, "Hoạt động đã bị huỷ", "“{meetupTitle}” của {hostName} đã bị huỷ. {reason}"),
                entry(NotificationType.MEETUP_INVITED, "Bạn được mời tham gia", "{hostName} mời bạn tham gia “{meetupTitle}”"),
                entry(NotificationType.LOCAL_EVENT_TOMORROW, "Sự kiện vào ngày mai", "“{eventTitle}” diễn ra vào ngày mai. Chạm để xem giờ và địa điểm"),
                entry(NotificationType.LOCAL_EVENT_STARTING_SOON, "Sắp bắt đầu", "“{eventTitle}” bắt đầu lúc {startTime}"),
                entry(NotificationType.LOCAL_EVENT_OPENING_TODAY, "Khai mạc hôm nay", "“{eventTitle}” mở cửa từ hôm nay"),
                entry(NotificationType.LOCAL_EVENT_ENDING_SOON, "Sắp kết thúc", "“{eventTitle}” kết thúc vào {endDate}. Bạn chưa check-in ở đây"),
                entry(NotificationType.LOCAL_EVENT_CANCELLED, "Sự kiện đã bị huỷ", "“{eventTitle}” đã bị huỷ"),
                entry(NotificationType.LOCAL_EVENT_POSTPONED, "Sự kiện tạm hoãn", "“{eventTitle}” đã tạm hoãn. Chưa có ngày mới"),
                entry(NotificationType.LOCAL_EVENT_RESCHEDULED, "Sự kiện đổi lịch", "“{eventTitle}” chuyển sang {startDate}"),
                entry(NotificationType.MAP_LIMIT_REACHED, "Đã hết lượt tìm kiếm bản đồ", "Bạn đã dùng hết lượt tìm kiếm bản đồ hôm nay. Lượt mới có lại lúc {resetTime}"),
                entry(NotificationType.MAP_SPAM_ALERT, "Nghi ngờ spam bản đồ", "{userName} đã bị chặn {rejectedCount} lượt gọi bản đồ hôm nay ({operation})"),
                entry(NotificationType.MAP_BUDGET_WARNING, "Cảnh báo ngân sách bản đồ", "Mapbox {api} đã dùng {usedPercent}% hạn mức tháng này"),
                entry(NotificationType.MAP_BUDGET_REACHED, "Đã chạm trần ngân sách bản đồ", "Mapbox {api} đã chạm trần; tìm kiếm dùng kết quả đã lưu đến {resetTime}"),
                entry(NotificationType.PLACE_EDIT_APPROVED, "Đã cập nhật địa điểm", "Cảm ơn bạn! Thông tin về {placeName} đã được cập nhật"),
                entry(NotificationType.PLACE_EDIT_REJECTED, "Đề xuất chưa được áp dụng", "Đề xuất của bạn cho {placeName} chưa được áp dụng"),
                entry(NotificationType.MEMBER_ACCESS_GRANTED, "Yêu cầu đã được chấp nhận", "Yêu cầu tham gia “{tripName}” của bạn đã được chấp nhận"),
                entry(NotificationType.MEMBER_JOIN_REJECTED, "Yêu cầu chưa được chấp nhận", "Yêu cầu tham gia “{tripName}” của bạn đã bị từ chối"),
                entry(NotificationType.MEMBER_ROLE_UPDATED, "Vai trò thành viên đã thay đổi", "{actorName} đã đổi vai trò của {memberName} trong “{tripName}” thành {role}"),
                entry(NotificationType.GUEST_UPDATED, "Thông tin khách đã thay đổi", "{actorName} đã đổi tên {previousGuestName} thành {guestName} trong “{tripName}”"),
                entry(NotificationType.CHECKIN_UPDATED, "Check-in đã thay đổi", "{actorName} đã cập nhật check-in tại “{activityName}” trong “{tripName}”"),
                entry(NotificationType.NOTE_UPDATED, "Ghi chú đã thay đổi", "{actorName} đã cập nhật ghi chú tại “{activityName}” trong “{tripName}”"),
                entry(NotificationType.MEMORY_ADDED, "Có kỷ niệm mới", "{actorName} đã thêm một kỷ niệm vào “{tripName}”"),
                entry(NotificationType.MEMORY_DELETED, "Đã gỡ kỷ niệm", "{actorName} đã gỡ một kỷ niệm khỏi “{tripName}”"),
                entry(NotificationType.TRIP_BOOK_UPDATED, "Sổ tay chuyến đi đã thay đổi", "{actorName} đã cập nhật sổ tay của “{tripName}”"),
                entry(NotificationType.TRIP_CLONED, "Chuyến đi đã được sao chép", "{actorName} đã sao chép chuyến đi “{tripName}” của bạn"),
                entry(NotificationType.ROUTE_OPTIMIZED, "Lộ trình đã sẵn sàng", "Lộ trình của “{tripName}” đã được tối ưu"),
                entry(NotificationType.TRIP_REMINDER, "Chuyến đi sắp bắt đầu", "“{tripName}” sắp diễn ra. Hãy dành ít phút xem lại lịch trình nhé"),
                entry(NotificationType.TRIP_STARTS_IN_ONE_WEEK, "Còn 1 tuần để chuẩn bị", "“{tripName}” sẽ bắt đầu sau 1 tuần. Hãy xem lại lịch trình và các đặt chỗ nhé"),
                entry(NotificationType.TRIP_STARTS_IN_THREE_DAYS, "Còn 3 ngày nữa", "“{tripName}” sẽ bắt đầu sau 3 ngày. Kiểm tra vé, phương tiện và các đặt chỗ để yên tâm lên đường nhé"),
                entry(NotificationType.TRIP_STARTS_IN_TWO_DAYS, "Còn 2 ngày nữa", "“{tripName}” sẽ bắt đầu sau 2 ngày. Hãy tải sẵn những thông tin cần dùng khi không có mạng"),
                entry(NotificationType.TRIP_STARTS_IN_ONE_DAY, "Ngày mai khởi hành", "“{tripName}” sẽ bắt đầu vào ngày mai. Xem lại lịch trình và hành lý lần cuối nhé"),
                entry(NotificationType.TRIP_STARTS_IN_TWO_HOURS, "Khởi hành sau 2 giờ", "“{tripName}” sẽ bắt đầu sau 2 giờ. Hãy kiểm tra điểm đến đầu tiên và dành đủ thời gian di chuyển"),
                entry(NotificationType.TRIP_STARTED, "Chuyến đi bắt đầu rồi", "“{tripName}” đã bắt đầu. Lịch trình đã sẵn sàng—chúc bạn có một chuyến đi thật vui!"),
                entry(NotificationType.ITINERARY_ITEM_PREPARATION, "Chuẩn bị cho {itemKindLabel}", "“{itemName}” sẽ bắt đầu sau {preparationLeadLabel}. {preparationChecklist}"),
                entry(NotificationType.ITINERARY_ITEM_UPCOMING, "Còn 15 phút", "“{itemName}” là {itemKindLabel} tiếp theo và sẽ bắt đầu sau 15 phút"),
                entry(NotificationType.ITINERARY_ITEM_COMPLETED, "“{itemName}” thế nào?", "Bạn vừa hoàn thành “{itemName}”. Chia sẻ một đánh giá ngắn để lưu lại trải nghiệm nhé. {nextItemSentence}"),
                entry(NotificationType.TRIP_DAY_ENDED, "Khép lại ngày {dayNumber}", "Ngày {dayNumber} của “{tripName}” đã xong. Check-in và đánh giá nhanh những nơi bạn đã ghé hôm nay nhé"),
                entry(NotificationType.TRIP_DAY_WEATHER_ALERT, "Dự báo ngày mai: {weatherLabel}", "Ngày {dayNumber} của “{tripName}” có thể bị ảnh hưởng. Xem lại lịch trình nếu cần đổi kế hoạch"),
                entry(NotificationType.TRIP_ENDED, "Chuyến đi đã khép lại", "“{tripName}” đã kết thúc. Cảm ơn bạn đã đồng hành cùng OnDetour!"),
                entry(NotificationType.TRIP_SUMMARY, "Nhìn lại chuyến đi", "Bạn đã chi {totalExpense} {currency} tại {placesCount} địa điểm. {debtSummary}"),
                entry(NotificationType.MARKETPLACE_BOOKING_REQUEST, "Yêu cầu đặt chỗ mới", "Có yêu cầu đặt chỗ mới từ khách đang chờ bạn phản hồi"),
                entry(NotificationType.MARKETPLACE_BOOKING_CONFIRMED, "Đặt chỗ đã được xác nhận", "Đặt chỗ của bạn đã được xác nhận. Mọi thứ đã sẵn sàng!"),
                entry(NotificationType.MARKETPLACE_BOOKING_DECLINED, "Đặt chỗ chưa được xác nhận", "Yêu cầu đặt chỗ chưa thể được xác nhận. Mở ứng dụng để xem các lựa chọn khác"),
                entry(NotificationType.MARKETPLACE_BOOKING_EXPIRED, "Yêu cầu đặt chỗ đã hết hạn", "Đối tác không phản hồi kịp nên yêu cầu của bạn đã được giải phóng. Bạn có thể đặt lại hoặc chọn lựa chọn khác"),
                entry(NotificationType.MARKETPLACE_BOOKING_CANCELLED_BY_GUEST, "Khách đã huỷ đặt chỗ", "Khách đã huỷ {bookingCode}. Tồn kho đã được trả lại"),
                entry(NotificationType.MARKETPLACE_BOOKING_UPDATED, "Đặt chỗ được cập nhật", "{bookingCode} hiện ở trạng thái {statusLabel}. Mở Đặt chỗ của tôi để xem chi tiết"),
                entry(NotificationType.MARKETPLACE_CHANGE_REQUESTED, "Yêu cầu thay đổi", "Khách muốn thay đổi {bookingCode}. Hãy xem xét trước khi yêu cầu hết hạn"),
                entry(NotificationType.MARKETPLACE_CHANGE_ANSWERED, "Yêu cầu thay đổi {decisionLabel}", "Yêu cầu thay đổi cho {bookingCode} đã {decisionLabel}. Mở đặt chỗ để xem chi tiết"),
                entry(NotificationType.MARKETPLACE_REVIEW_INVITE, "Chuyến đi thế nào?", "Đặt chỗ {bookingCode} đã hoàn tất. Chia sẻ đánh giá để giúp những người đi sau"),
                entry(NotificationType.MARKETPLACE_MESSAGE, "Tin nhắn mới", "{senderName} đã nhắn tin cho bạn"),
                entry(NotificationType.CHAT_MENTION, "Bạn được nhắc đến", "{senderName} đã nhắc đến bạn trong {conversationTitle}"),
                entry(NotificationType.PARTNER_VERIFICATION_DECIDED, "Xác minh {decisionLabel}", "Tổ chức {organizationName} đã {decisionLabel}. {reason}"),
                entry(NotificationType.PARTNER_VERIFICATION_SUBMITTED, "Đối tác nộp hồ sơ xác minh", "{organizationName} vừa nộp hồ sơ xác minh"),
                entry(NotificationType.PARTNER_STATEMENT_READY, "Sao kê đã sẵn sàng", "Sao kê kỳ {periodLabel} đã có: còn {netAmount} sau hoa hồng. Mở mục Tài chính để đối soát"),
                entry(NotificationType.ADMIN_ANNOUNCEMENT, "Thông báo từ OnDetour", "{body}"),
                entry(NotificationType.ADMIN_MESSAGE, "Tin nhắn từ OnDetour", "{body}")
        );
    }

    private Map<NotificationType, NotificationMessage> japanese() {
        return Map.ofEntries(
                entry(NotificationType.EXPENSE_ADDED, "支出を追加", "{actorName}さんが「{tripName}」に「{expenseName}」（{amount} {currency}）を追加しました"),
                entry(NotificationType.EXPENSE_UPDATED, "支出を更新", "{actorName}さんが「{tripName}」の支出「{expenseName}」を更新しました"),
                entry(NotificationType.EXPENSE_DELETED, "支出を削除", "{actorName}さんが「{tripName}」から支出「{expenseName}」を削除しました"),
                entry(NotificationType.ACTIVITY_ADDED, "予定を追加", "{actorName}さんが「{tripName}」に予定「{activityName}」を追加しました"),
                entry(NotificationType.ACTIVITY_UPDATED, "予定を更新", "{actorName}さんが「{tripName}」の予定「{activityName}」を更新しました"),
                entry(NotificationType.ACTIVITY_DELETED, "予定を削除", "{actorName}さんが「{tripName}」から予定「{activityName}」を削除しました"),
                entry(NotificationType.MEMBER_ADDED, "新しいメンバー", "{actorName}さんが「{tripName}」に{newMemberName}さんを追加しました"),
                entry(NotificationType.MEMBER_REMOVED, "メンバーを削除", "{actorName}さんが「{tripName}」から{removedMemberName}さんを削除しました"),
                entry(NotificationType.MEMBER_ACCEPTED, "メンバーが参加しました", "{memberName}さんが「{tripName}」に参加しました"),
                entry(NotificationType.MEMBER_JOINED, "メンバーが参加しました", "{memberName}さんが「{tripName}」に参加しました"),
                entry(NotificationType.MEMBER_LEFT, "メンバーが退出しました", "{actorName}さんが「{tripName}」から退出しました"),
                entry(NotificationType.GUEST_LINKED, "ゲストをリンク", "「{tripName}」で{guestName}さんが{linkedUserName}さんにリンクされました"),
                entry(NotificationType.TRIP_UPDATED, "旅行を更新", "{actorName}さんが旅行「{tripName}」を更新しました"),
                entry(NotificationType.TRIP_DELETED, "旅行を削除", "{actorName}さんが旅行「{tripName}」を削除しました"),
                entry(NotificationType.PAYMENT_MARKED, "支払いを更新", "{payerName}さんが「{expenseDescription}」の{payeeName}さんの支払い（{amount} {currency}）を更新しました"),
                entry(NotificationType.PAYMENT_REMINDER, "支払いのリマインダー", "{actorName}さんが「{expenseDescription}」の{amount} {currency}についてリマインドしました。"),
                entry(NotificationType.SETTLEMENT_ADDED, "支払いを記録しました", "{payerName}さんが{payeeName}さんに{amount} {currency}を支払いました。"),
                entry(NotificationType.TODO_ASSIGNED, "タスクが割り当てられました", "{actorName}さんが「{tripName}」で「{todoText}」をあなたに割り当てました"),
                entry(NotificationType.PAYMENT_ALL_MARKED, "支払いを更新", "{actorName}さんが「{expenseDescription}」の支払いを{paidStatus}にしました"),
                entry(NotificationType.PAYMENT_TRIP_MARKED, "旅行の支払いを更新", "{actorName}さんが「{tripName}」の支払いを{paidStatus}にしました"),
                entry(NotificationType.CHECKIN, "チェックイン", "{actorName}さんが「{tripName}」の「{activityName}」にチェックインしました"),
                entry(NotificationType.NOTE_ADDED, "ノートを追加", "{actorName}さんが「{tripName}」の「{activityName}」にノートを追加しました"),
                entry(NotificationType.NOTE_DELETED, "ノートを削除", "{actorName}さんが「{tripName}」の「{activityName}」からノートを削除しました"),
                entry(NotificationType.COMMENT_ADDED, "コメントを追加", "{actorName}さんが「{tripName}」の「{activityName}」にコメントしました"),
                entry(NotificationType.COMMENT_DELETED, "コメントを削除", "{actorName}さんが「{tripName}」の「{activityName}」からコメントを削除しました"),
                entry(NotificationType.TRIP_INVITE, "旅行への招待", "「{tripName}」に招待されました"),
                entry(NotificationType.TRIP_INVITE_DECLINED, "招待が辞退されました", "{memberName}さんが「{tripName}」への招待を辞退しました"),
                entry(NotificationType.TRIP_INVITE_CANCELLED, "招待が取り消されました", "「{tripName}」への招待が取り消されました"),
                entry(NotificationType.MEMBER_INVITED, "メンバーを招待", "{actorName}さんが{newMemberName}さんを「{tripName}」に招待しました"),
                entry(NotificationType.MEMBER_JOIN_REQUESTED, "参加リクエスト", "{memberName}さんが「{tripName}」への参加を申請しました"),
                entry(NotificationType.MEETUP_APPROVED, "アクティビティが承認されました", "「{meetupTitle}」が地図に表示されました"),
                entry(NotificationType.MEETUP_REJECTED, "アクティビティは承認されませんでした", "「{meetupTitle}」は承認されませんでした。{reason}"),
                entry(NotificationType.MEETUP_REMOVED, "アクティビティが削除されました", "「{meetupTitle}」は地図から削除されました。{reason}"),
                entry(NotificationType.MEETUP_JOIN_REQUESTED, "新しい参加リクエスト", "{userName}さんが「{meetupTitle}」への参加をリクエストしました"),
                entry(NotificationType.MEETUP_PARTICIPANT_JOINED, "新しい参加者", "{userName}さんが「{meetupTitle}」に参加しました"),
                entry(NotificationType.MEETUP_REQUEST_ACCEPTED, "リクエストが承認されました", "{hostName}さんが「{meetupTitle}」への参加を承認しました"),
                entry(NotificationType.MEETUP_REQUEST_DECLINED, "リクエストは承認されませんでした", "{hostName}さんは「{meetupTitle}」への参加リクエストを承認できませんでした"),
                entry(NotificationType.MEETUP_CANCELLED, "アクティビティが中止されました", "{hostName}さんの「{meetupTitle}」は中止されました。{reason}"),
                entry(NotificationType.MEETUP_INVITED, "招待が届きました", "{hostName}さんが「{meetupTitle}」に招待しました"),
                entry(NotificationType.LOCAL_EVENT_TOMORROW, "明日のイベント", "「{eventTitle}」は明日です。タップして時間と場所を確認"),
                entry(NotificationType.LOCAL_EVENT_STARTING_SOON, "まもなく開始", "「{eventTitle}」は{startTime}に始まります"),
                entry(NotificationType.LOCAL_EVENT_OPENING_TODAY, "本日開幕", "「{eventTitle}」は本日から開催です"),
                entry(NotificationType.LOCAL_EVENT_ENDING_SOON, "まもなく終了", "「{eventTitle}」は{endDate}に終了します。まだチェックインしていません"),
                entry(NotificationType.LOCAL_EVENT_CANCELLED, "イベント中止", "「{eventTitle}」は中止されました"),
                entry(NotificationType.LOCAL_EVENT_POSTPONED, "イベント延期", "「{eventTitle}」は延期されました。新しい日程は未定です"),
                entry(NotificationType.LOCAL_EVENT_RESCHEDULED, "日程変更", "「{eventTitle}」は{startDate}に変更されました"),
                entry(NotificationType.MAP_LIMIT_REACHED, "地図検索の上限に達しました", "本日の地図検索の回数を使い切りました。{resetTime}に再び使えるようになります"),
                entry(NotificationType.MAP_SPAM_ALERT, "地図APIの不正利用の疑い", "{userName}さんは本日{rejectedCount}回の地図リクエストを拒否されました（{operation}）"),
                entry(NotificationType.MAP_BUDGET_WARNING, "地図の予算警告", "Mapbox {api}は今月の上限の{usedPercent}%を使用しました"),
                entry(NotificationType.MAP_BUDGET_REACHED, "地図の予算上限に達しました", "Mapbox {api}が上限に達しました。{resetTime}まで保存済みの結果で検索します"),
                entry(NotificationType.PLACE_EDIT_APPROVED, "スポット情報を更新しました", "ありがとうございます！{placeName}の情報が更新されました"),
                entry(NotificationType.PLACE_EDIT_REJECTED, "提案は反映されませんでした", "{placeName}へのご提案は反映されませんでした"),
                entry(NotificationType.MEMBER_ACCESS_GRANTED, "参加が承認されました", "「{tripName}」への参加リクエストが承認されました"),
                entry(NotificationType.MEMBER_JOIN_REJECTED, "参加が承認されませんでした", "「{tripName}」への参加リクエストが却下されました"),
                entry(NotificationType.MEMBER_ROLE_UPDATED, "役割を更新", "{actorName}さんが「{tripName}」で{memberName}さんの役割を{role}に変更しました"),
                entry(NotificationType.GUEST_UPDATED, "ゲストを更新", "{actorName}さんが「{tripName}」で{previousGuestName}を{guestName}に変更しました"),
                entry(NotificationType.CHECKIN_UPDATED, "チェックインを更新", "{actorName}さんが「{tripName}」の「{activityName}」でチェックインを更新しました"),
                entry(NotificationType.NOTE_UPDATED, "ノートを更新", "{actorName}さんが「{tripName}」の「{activityName}」でノートを更新しました"),
                entry(NotificationType.MEMORY_ADDED, "思い出を追加", "{actorName}さんが「{tripName}」に思い出を追加しました"),
                entry(NotificationType.MEMORY_DELETED, "思い出を削除", "{actorName}さんが「{tripName}」から思い出を削除しました"),
                entry(NotificationType.TRIP_BOOK_UPDATED, "トラベルブックを更新", "{actorName}さんが「{tripName}」のトラベルブックを更新しました"),
                entry(NotificationType.TRIP_CLONED, "旅行がコピーされました", "{actorName}さんがあなたの旅行「{tripName}」をコピーしました"),
                entry(NotificationType.ROUTE_OPTIMIZED, "ルートを最適化", "「{tripName}」のルートが最適化されました"),
                entry(NotificationType.TRIP_REMINDER, "旅行が近づいています", "「{tripName}」の予定を少し確認しておきましょう"),
                entry(NotificationType.TRIP_STARTS_IN_ONE_WEEK, "出発まであと1週間", "「{tripName}」は1週間後に始まります。旅程や予約を確認しておきましょう"),
                entry(NotificationType.TRIP_STARTS_IN_THREE_DAYS, "出発まであと3日", "「{tripName}」は3日後に始まります。チケット、移動手段、予約をご確認ください"),
                entry(NotificationType.TRIP_STARTS_IN_TWO_DAYS, "出発まであと2日", "「{tripName}」は2日後に始まります。オフラインで必要な情報を保存しておきましょう"),
                entry(NotificationType.TRIP_STARTS_IN_ONE_DAY, "いよいよ明日出発", "「{tripName}」は明日始まります。旅程と持ち物を最後に確認しましょう"),
                entry(NotificationType.TRIP_STARTS_IN_TWO_HOURS, "出発まであと2時間", "「{tripName}」は2時間後に始まります。最初の目的地と移動時間をご確認ください"),
                entry(NotificationType.TRIP_STARTED, "旅行が始まりました", "「{tripName}」が始まりました。すてきな旅をお楽しみください！"),
                entry(NotificationType.ITINERARY_ITEM_PREPARATION, "{itemKindLabel}の準備", "「{itemName}」は{preparationLeadLabel}後に始まります。{preparationChecklist}"),
                entry(NotificationType.ITINERARY_ITEM_UPCOMING, "あと15分です", "次の{itemKindLabel}「{itemName}」は15分後に始まります"),
                entry(NotificationType.ITINERARY_ITEM_COMPLETED, "「{itemName}」はいかがでしたか？", "「{itemName}」が終わりました。短いレビューで思い出を残しませんか。{nextItemSentence}"),
                entry(NotificationType.TRIP_DAY_ENDED, "{dayNumber}日目おつかれさまでした", "「{tripName}」の{dayNumber}日目が終わりました。訪れた場所にチェックインして、短いレビューを残しましょう"),
                entry(NotificationType.TRIP_DAY_WEATHER_ALERT, "明日の天気予報：{weatherLabel}", "「{tripName}」の{dayNumber}日目に影響が出るかもしれません。必要なら予定を見直しましょう"),
                entry(NotificationType.TRIP_ENDED, "おかえりなさい", "「{tripName}」が終了しました。すてきな旅になったことを願っています！"),
                entry(NotificationType.TRIP_SUMMARY, "旅の振り返り", "{placesCount}か所で合計{totalExpense} {currency}を使いました。{debtSummary}"),
                entry(NotificationType.MARKETPLACE_BOOKING_REQUEST, "新しい予約リクエスト", "ゲストから予約リクエストが届きました。内容をご確認ください"),
                entry(NotificationType.MARKETPLACE_BOOKING_CONFIRMED, "予約が確定しました", "予約が確定しました。準備は完了です！"),
                entry(NotificationType.MARKETPLACE_BOOKING_DECLINED, "予約を確定できませんでした", "予約リクエストを確定できませんでした。アプリで別の選択肢をご確認ください"),
                entry(NotificationType.ADMIN_ANNOUNCEMENT, "OnDetourからのお知らせ", "{body}"),
                entry(NotificationType.ADMIN_MESSAGE, "OnDetourからのメッセージ", "{body}")
        );
    }

    private Map<NotificationType, NotificationMessage> korean() {
        return Map.ofEntries(
                entry(NotificationType.EXPENSE_ADDED, "비용이 추가되었습니다", "{actorName}님이 “{tripName}”에 “{expenseName}” 비용 {amount} {currency}를 추가했습니다"),
                entry(NotificationType.EXPENSE_UPDATED, "비용이 수정되었습니다", "{actorName}님이 “{tripName}”의 “{expenseName}” 비용을 수정했습니다"),
                entry(NotificationType.EXPENSE_DELETED, "비용이 삭제되었습니다", "{actorName}님이 “{tripName}”에서 “{expenseName}” 비용을 삭제했습니다"),
                entry(NotificationType.ACTIVITY_ADDED, "일정이 추가되었습니다", "{actorName}님이 “{tripName}”에 “{activityName}” 일정을 추가했습니다"),
                entry(NotificationType.ACTIVITY_UPDATED, "일정이 수정되었습니다", "{actorName}님이 “{tripName}”의 “{activityName}” 일정을 수정했습니다"),
                entry(NotificationType.ACTIVITY_DELETED, "일정이 삭제되었습니다", "{actorName}님이 “{tripName}”에서 “{activityName}” 일정을 삭제했습니다"),
                entry(NotificationType.MEMBER_ADDED, "새 멤버", "{actorName}님이 “{tripName}”에 {newMemberName}님을 추가했습니다"),
                entry(NotificationType.MEMBER_REMOVED, "멤버가 삭제되었습니다", "{actorName}님이 “{tripName}”에서 {removedMemberName}님을 삭제했습니다"),
                entry(NotificationType.MEMBER_ACCEPTED, "멤버가 참여했습니다", "{memberName}님이 “{tripName}”에 참여했습니다"),
                entry(NotificationType.MEMBER_JOINED, "멤버가 참여했습니다", "{memberName}님이 “{tripName}”에 참여했습니다"),
                entry(NotificationType.MEMBER_LEFT, "멤버가 나갔습니다", "{actorName}님이 “{tripName}”에서 나갔습니다"),
                entry(NotificationType.GUEST_LINKED, "게스트가 연결되었습니다", "“{tripName}”에서 {guestName}님이 {linkedUserName}님과 연결되었습니다"),
                entry(NotificationType.TRIP_UPDATED, "여행이 수정되었습니다", "{actorName}님이 “{tripName}” 여행을 수정했습니다"),
                entry(NotificationType.TRIP_DELETED, "여행이 삭제되었습니다", "{actorName}님이 “{tripName}” 여행을 삭제했습니다"),
                entry(NotificationType.PAYMENT_MARKED, "결제가 업데이트되었습니다", "{payerName}님이 “{expenseDescription}”에 대한 {payeeName}님의 결제 {amount} {currency}를 업데이트했습니다"),
                entry(NotificationType.PAYMENT_REMINDER, "결제 알림", "{actorName}님이 “{expenseDescription}”의 {amount} {currency} 결제를 알려드립니다."),
                entry(NotificationType.SETTLEMENT_ADDED, "결제가 기록되었습니다", "{payerName}님이 {payeeName}님에게 {amount} {currency}을(를) 지불했습니다."),
                entry(NotificationType.TODO_ASSIGNED, "할 일이 배정되었습니다", "{actorName}님이 “{tripName}”에서 “{todoText}”을(를) 회원님에게 배정했습니다"),
                entry(NotificationType.PAYMENT_ALL_MARKED, "비용 결제가 업데이트되었습니다", "{actorName}님이 “{expenseDescription}” 결제를 {paidStatus} 상태로 표시했습니다"),
                entry(NotificationType.PAYMENT_TRIP_MARKED, "여행 결제가 업데이트되었습니다", "{actorName}님이 “{tripName}”의 결제를 {paidStatus} 상태로 표시했습니다"),
                entry(NotificationType.CHECKIN, "체크인", "{actorName}님이 “{tripName}”의 “{activityName}”에 체크인했습니다"),
                entry(NotificationType.NOTE_ADDED, "노트가 추가되었습니다", "{actorName}님이 “{tripName}”의 “{activityName}”에 노트를 추가했습니다"),
                entry(NotificationType.NOTE_DELETED, "노트가 삭제되었습니다", "{actorName}님이 “{tripName}”의 “{activityName}”에서 노트를 삭제했습니다"),
                entry(NotificationType.COMMENT_ADDED, "댓글이 추가되었습니다", "{actorName}님이 “{tripName}”의 “{activityName}”에 댓글을 남겼습니다"),
                entry(NotificationType.COMMENT_DELETED, "댓글이 삭제되었습니다", "{actorName}님이 “{tripName}”의 “{activityName}”에서 댓글을 삭제했습니다"),
                entry(NotificationType.TRIP_INVITE, "여행 초대", "“{tripName}”에 초대되었습니다"),
                entry(NotificationType.TRIP_INVITE_DECLINED, "초대가 거절되었습니다", "{memberName}님이 “{tripName}” 초대를 거절했습니다"),
                entry(NotificationType.TRIP_INVITE_CANCELLED, "초대가 취소되었습니다", "“{tripName}” 초대가 취소되었습니다"),
                entry(NotificationType.MEMBER_INVITED, "멤버를 초대했습니다", "{actorName}님이 {newMemberName}님을 “{tripName}”에 초대했습니다"),
                entry(NotificationType.MEMBER_JOIN_REQUESTED, "참여 요청", "{memberName}님이 “{tripName}” 참여를 요청했습니다"),
                entry(NotificationType.MEETUP_APPROVED, "활동이 승인되었습니다", "“{meetupTitle}” 활동이 지도에 표시되었습니다"),
                entry(NotificationType.MEETUP_REJECTED, "활동이 승인되지 않았습니다", "“{meetupTitle}” 활동이 승인되지 않았습니다. {reason}"),
                entry(NotificationType.MEETUP_REMOVED, "활동이 삭제되었습니다", "“{meetupTitle}” 활동이 지도에서 삭제되었습니다. {reason}"),
                entry(NotificationType.MEETUP_JOIN_REQUESTED, "새 참여 요청", "{userName}님이 “{meetupTitle}” 참여를 요청했습니다"),
                entry(NotificationType.MEETUP_PARTICIPANT_JOINED, "새 참여자", "{userName}님이 “{meetupTitle}”에 참여했습니다"),
                entry(NotificationType.MEETUP_REQUEST_ACCEPTED, "요청이 수락되었습니다", "{hostName}님이 “{meetupTitle}” 참여 요청을 수락했습니다"),
                entry(NotificationType.MEETUP_REQUEST_DECLINED, "요청이 수락되지 않았습니다", "{hostName}님이 “{meetupTitle}” 참여 요청을 수락하지 못했습니다"),
                entry(NotificationType.MEETUP_CANCELLED, "활동이 취소되었습니다", "{hostName}님의 “{meetupTitle}” 활동이 취소되었습니다. {reason}"),
                entry(NotificationType.MEETUP_INVITED, "초대를 받았습니다", "{hostName}님이 “{meetupTitle}”에 초대했습니다"),
                entry(NotificationType.LOCAL_EVENT_TOMORROW, "내일 열리는 이벤트", "“{eventTitle}” 이벤트가 내일 열립니다. 눌러서 시간과 장소를 확인하세요"),
                entry(NotificationType.LOCAL_EVENT_STARTING_SOON, "곧 시작", "“{eventTitle}” 이벤트가 {startTime}에 시작합니다"),
                entry(NotificationType.LOCAL_EVENT_OPENING_TODAY, "오늘 개막", "“{eventTitle}” 이벤트가 오늘 시작됩니다"),
                entry(NotificationType.LOCAL_EVENT_ENDING_SOON, "곧 종료", "“{eventTitle}” 이벤트가 {endDate}에 끝납니다. 아직 체크인하지 않았습니다"),
                entry(NotificationType.LOCAL_EVENT_CANCELLED, "이벤트 취소", "“{eventTitle}” 이벤트가 취소되었습니다"),
                entry(NotificationType.LOCAL_EVENT_POSTPONED, "이벤트 연기", "“{eventTitle}” 이벤트가 연기되었습니다. 새 일정은 아직 없습니다"),
                entry(NotificationType.LOCAL_EVENT_RESCHEDULED, "일정 변경", "“{eventTitle}” 이벤트가 {startDate}(으)로 변경되었습니다"),
                entry(NotificationType.MAP_LIMIT_REACHED, "지도 검색 한도에 도달했습니다", "오늘의 지도 검색 횟수를 모두 사용했습니다. {resetTime}에 다시 사용할 수 있습니다"),
                entry(NotificationType.MAP_SPAM_ALERT, "지도 스팸 의심", "{userName}님의 지도 요청이 오늘 {rejectedCount}회 거부되었습니다({operation})"),
                entry(NotificationType.MAP_BUDGET_WARNING, "지도 예산 경고", "Mapbox {api}가 이번 달 한도의 {usedPercent}%를 사용했습니다"),
                entry(NotificationType.MAP_BUDGET_REACHED, "지도 예산 한도 도달", "Mapbox {api}가 한도에 도달했습니다. {resetTime}까지 저장된 결과로 검색합니다"),
                entry(NotificationType.PLACE_EDIT_APPROVED, "장소 정보가 업데이트되었습니다", "감사합니다! {placeName} 정보가 업데이트되었습니다"),
                entry(NotificationType.PLACE_EDIT_REJECTED, "제안이 반영되지 않았습니다", "{placeName}에 대한 제안이 반영되지 않았습니다"),
                entry(NotificationType.MEMBER_ACCESS_GRANTED, "참여 요청이 승인되었습니다", "“{tripName}” 참여 요청이 승인되었습니다"),
                entry(NotificationType.MEMBER_JOIN_REJECTED, "참여 요청이 거절되었습니다", "“{tripName}” 참여 요청이 거절되었습니다"),
                entry(NotificationType.MEMBER_ROLE_UPDATED, "멤버 역할이 변경되었습니다", "{actorName}님이 “{tripName}”에서 {memberName}님의 역할을 {role}(으)로 변경했습니다"),
                entry(NotificationType.GUEST_UPDATED, "게스트 정보가 변경되었습니다", "{actorName}님이 “{tripName}”에서 {previousGuestName}을 {guestName}(으)로 변경했습니다"),
                entry(NotificationType.CHECKIN_UPDATED, "체크인이 수정되었습니다", "{actorName}님이 “{tripName}”의 “{activityName}” 체크인을 수정했습니다"),
                entry(NotificationType.NOTE_UPDATED, "노트가 수정되었습니다", "{actorName}님이 “{tripName}”의 “{activityName}” 노트를 수정했습니다"),
                entry(NotificationType.MEMORY_ADDED, "새 추억", "{actorName}님이 “{tripName}”에 추억을 추가했습니다"),
                entry(NotificationType.MEMORY_DELETED, "추억이 삭제되었습니다", "{actorName}님이 “{tripName}”에서 추억을 삭제했습니다"),
                entry(NotificationType.TRIP_BOOK_UPDATED, "트래블 북이 수정되었습니다", "{actorName}님이 “{tripName}”의 트래블 북을 수정했습니다"),
                entry(NotificationType.TRIP_CLONED, "여행이 복사되었습니다", "{actorName}님이 회원님의 여행 “{tripName}”을 복사했습니다"),
                entry(NotificationType.ROUTE_OPTIMIZED, "경로 최적화 완료", "“{tripName}”의 경로가 최적화되었습니다"),
                entry(NotificationType.TRIP_REMINDER, "여행이 다가오고 있어요", "“{tripName}” 일정을 잠시 확인해 주세요"),
                entry(NotificationType.TRIP_STARTS_IN_ONE_WEEK, "출발까지 1주일", "“{tripName}” 여행이 1주일 후 시작됩니다. 일정과 예약을 미리 확인해 주세요"),
                entry(NotificationType.TRIP_STARTS_IN_THREE_DAYS, "출발까지 3일", "“{tripName}” 여행이 3일 후 시작됩니다. 티켓, 교통편과 예약을 확인해 주세요"),
                entry(NotificationType.TRIP_STARTS_IN_TWO_DAYS, "출발까지 2일", "“{tripName}” 여행이 2일 후 시작됩니다. 오프라인에서 필요한 정보를 미리 저장해 주세요"),
                entry(NotificationType.TRIP_STARTS_IN_ONE_DAY, "내일 출발해요", "“{tripName}” 여행이 내일 시작됩니다. 일정과 준비물을 마지막으로 확인해 주세요"),
                entry(NotificationType.TRIP_STARTS_IN_TWO_HOURS, "출발까지 2시간", "“{tripName}” 여행이 2시간 후 시작됩니다. 첫 목적지와 이동 시간을 확인해 주세요"),
                entry(NotificationType.TRIP_STARTED, "여행이 시작됐어요", "“{tripName}” 여행이 시작되었습니다. 즐거운 여행 되세요!"),
                entry(NotificationType.ITINERARY_ITEM_PREPARATION, "{itemKindLabel} 준비", "“{itemName}” 일정이 {preparationLeadLabel} 후 시작됩니다. {preparationChecklist}"),
                entry(NotificationType.ITINERARY_ITEM_UPCOMING, "15분 남았어요", "다음 {itemKindLabel} “{itemName}” 일정이 15분 후 시작됩니다"),
                entry(NotificationType.ITINERARY_ITEM_COMPLETED, "“{itemName}” 어떠셨나요?", "“{itemName}” 일정이 끝났습니다. 짧은 리뷰로 추억을 남겨 주세요. {nextItemSentence}"),
                entry(NotificationType.TRIP_DAY_ENDED, "{dayNumber}일차 마무리", "“{tripName}” {dayNumber}일차가 끝났습니다. 오늘 들른 곳에 체크인하고 짧은 리뷰를 남겨 보세요"),
                entry(NotificationType.TRIP_DAY_WEATHER_ALERT, "내일 날씨 예보: {weatherLabel}", "“{tripName}” {dayNumber}일차 일정에 영향이 있을 수 있어요. 필요하면 일정을 다시 확인해 보세요"),
                entry(NotificationType.TRIP_ENDED, "여행을 마쳤어요", "“{tripName}” 여행이 끝났습니다. 즐거운 여행이었기를 바랍니다!"),
                entry(NotificationType.TRIP_SUMMARY, "여행 돌아보기", "{placesCount}곳에서 총 {totalExpense} {currency}를 사용했습니다. {debtSummary}"),
                entry(NotificationType.MARKETPLACE_BOOKING_REQUEST, "새 예약 요청", "게스트의 예약 요청이 도착했습니다. 내용을 확인해 주세요"),
                entry(NotificationType.MARKETPLACE_BOOKING_CONFIRMED, "예약이 확정됐어요", "예약이 확정되었습니다. 이제 준비가 끝났어요!"),
                entry(NotificationType.MARKETPLACE_BOOKING_DECLINED, "예약을 확정하지 못했어요", "예약 요청을 확정하지 못했습니다. 앱에서 다른 옵션을 확인해 주세요"),
                entry(NotificationType.ADMIN_ANNOUNCEMENT, "OnDetour 공지사항", "{body}"),
                entry(NotificationType.ADMIN_MESSAGE, "OnDetour 메시지", "{body}")
        );
    }

    private Map<NotificationType, NotificationMessage> hindi() {
        return Map.ofEntries(
                entry(NotificationType.EXPENSE_ADDED, "नया खर्च जोड़ा गया", "{actorName} ने “{tripName}” में “{expenseName}” के लिए {amount} {currency} जोड़ा"),
                entry(NotificationType.EXPENSE_UPDATED, "खर्च अपडेट हुआ", "{actorName} ने {tripName} में “{expenseName}” अपडेट किया"),
                entry(NotificationType.EXPENSE_DELETED, "खर्च हटाया गया", "{actorName} ने {tripName} से “{expenseName}” हटा दिया"),
                entry(NotificationType.ACTIVITY_ADDED, "गतिविधि जोड़ी गई", "{actorName} ने “{tripName}” में “{activityName}” जोड़ी"),
                entry(NotificationType.ACTIVITY_UPDATED, "गतिविधि अपडेट हुई", "{actorName} ने “{tripName}” में “{activityName}” अपडेट की"),
                entry(NotificationType.ACTIVITY_DELETED, "गतिविधि हटाई गई", "{actorName} ने “{tripName}” से “{activityName}” हटा दी"),
                entry(NotificationType.MEMBER_ADDED, "नया सदस्य", "{actorName} ने {newMemberName} को {tripName} में जोड़ा"),
                entry(NotificationType.MEMBER_REMOVED, "सदस्य हटाया गया", "{actorName} ने {removedMemberName} को {tripName} से हटा दिया"),
                entry(NotificationType.MEMBER_ACCEPTED, "सदस्य शामिल हुआ", "{memberName} {tripName} में शामिल हुए"),
                entry(NotificationType.MEMBER_JOINED, "सदस्य शामिल हुआ", "{memberName} {tripName} में शामिल हुए"),
                entry(NotificationType.MEMBER_LEFT, "सदस्य चला गया", "{actorName} ने {tripName} छोड़ दिया"),
                entry(NotificationType.GUEST_LINKED, "अतिथि लिंक हुआ", "{tripName} में {guestName} को {linkedUserName} से लिंक किया गया"),
                entry(NotificationType.TRIP_UPDATED, "यात्रा अपडेट हुई", "{actorName} ने यात्रा {tripName} अपडेट की"),
                entry(NotificationType.TRIP_DELETED, "यात्रा हटाई गई", "{actorName} ने यात्रा {tripName} हटा दी"),
                entry(NotificationType.PAYMENT_MARKED, "भुगतान अपडेट हुआ", "{payerName} ने “{expenseDescription}” के लिए {payeeName} का {amount} {currency} भुगतान अपडेट किया"),
                entry(NotificationType.PAYMENT_REMINDER, "भुगतान रिमाइंडर", "{actorName} ने “{expenseDescription}” के लिए {amount} {currency} के भुगतान की याद दिलाई।"),
                entry(NotificationType.SETTLEMENT_ADDED, "भुगतान दर्ज किया गया", "{payerName} ने {payeeName} को {amount} {currency} का भुगतान किया।"),
                entry(NotificationType.TODO_ASSIGNED, "आपको काम सौंपा गया", "{actorName} ने “{tripName}” में आपको “{todoText}” सौंपा"),
                entry(NotificationType.PAYMENT_ALL_MARKED, "खर्च भुगतान अपडेट हुए", "{actorName} ने “{expenseDescription}” के भुगतान को {paidStatus} चिह्नित किया"),
                entry(NotificationType.PAYMENT_TRIP_MARKED, "यात्रा भुगतान अपडेट हुए", "{actorName} ने {tripName} के भुगतान को {paidStatus} चिह्नित किया"),
                entry(NotificationType.CHECKIN, "चेक-इन", "{actorName} ने {tripName} में “{activityName}” पर चेक-इन किया"),
                entry(NotificationType.NOTE_ADDED, "नोट जोड़ा गया", "{actorName} ने “{tripName}” में “{activityName}” के लिए एक नोट जोड़ा"),
                entry(NotificationType.NOTE_DELETED, "नोट हटाया गया", "{actorName} ने “{tripName}” से “{activityName}” का नोट हटाया"),
                entry(NotificationType.COMMENT_ADDED, "टिप्पणी जोड़ी गई", "{actorName} ने {tripName} में “{activityName}” पर टिप्पणी की"),
                entry(NotificationType.COMMENT_DELETED, "टिप्पणी हटाई गई", "{actorName} ने {tripName} में “{activityName}” से टिप्पणी हटा दी"),
                entry(NotificationType.TRIP_INVITE, "यात्रा का निमंत्रण", "आपको {tripName} में शामिल होने के लिए आमंत्रित किया गया है"),
                entry(NotificationType.TRIP_INVITE_DECLINED, "निमंत्रण अस्वीकार हुआ", "{memberName} ने {tripName} का निमंत्रण अस्वीकार किया"),
                entry(NotificationType.TRIP_INVITE_CANCELLED, "निमंत्रण रद्द हुआ", "{tripName} का आपका निमंत्रण रद्द कर दिया गया"),
                entry(NotificationType.MEMBER_INVITED, "सदस्य आमंत्रित", "{actorName} ने {newMemberName} को {tripName} में आमंत्रित किया"),
                entry(NotificationType.MEMBER_JOIN_REQUESTED, "शामिल होने का अनुरोध", "{memberName} ने {tripName} में शामिल होने का अनुरोध किया"),
                entry(NotificationType.MEETUP_APPROVED, "गतिविधि स्वीकृत हुई", "“{meetupTitle}” अब मानचित्र पर दिख रही है"),
                entry(NotificationType.MEETUP_REJECTED, "गतिविधि स्वीकृत नहीं हुई", "“{meetupTitle}” स्वीकृत नहीं हुई। {reason}"),
                entry(NotificationType.MEETUP_REMOVED, "गतिविधि हटाई गई", "“{meetupTitle}” को मानचित्र से हटा दिया गया। {reason}"),
                entry(NotificationType.MEETUP_JOIN_REQUESTED, "शामिल होने का नया अनुरोध", "{userName} ने “{meetupTitle}” में शामिल होने का अनुरोध किया"),
                entry(NotificationType.MEETUP_PARTICIPANT_JOINED, "कोई शामिल हुआ", "{userName} “{meetupTitle}” में शामिल हुए"),
                entry(NotificationType.MEETUP_REQUEST_ACCEPTED, "अनुरोध स्वीकार हुआ", "{hostName} ने “{meetupTitle}” में शामिल होने का आपका अनुरोध स्वीकार किया"),
                entry(NotificationType.MEETUP_REQUEST_DECLINED, "अनुरोध स्वीकार नहीं हुआ", "{hostName} “{meetupTitle}” में शामिल होने का आपका अनुरोध स्वीकार नहीं कर सके"),
                entry(NotificationType.MEETUP_CANCELLED, "गतिविधि रद्द हुई", "{hostName} की “{meetupTitle}” रद्द कर दी गई। {reason}"),
                entry(NotificationType.MEETUP_INVITED, "आपको आमंत्रित किया गया", "{hostName} ने आपको “{meetupTitle}” में आमंत्रित किया"),
                entry(NotificationType.LOCAL_EVENT_TOMORROW, "कल का कार्यक्रम", "“{eventTitle}” कल है। समय और जगह देखने के लिए टैप करें"),
                entry(NotificationType.LOCAL_EVENT_STARTING_SOON, "जल्द शुरू", "“{eventTitle}” {startTime} बजे शुरू होगा"),
                entry(NotificationType.LOCAL_EVENT_OPENING_TODAY, "आज से शुरू", "“{eventTitle}” आज से खुल रहा है"),
                entry(NotificationType.LOCAL_EVENT_ENDING_SOON, "जल्द समाप्त", "“{eventTitle}” {endDate} को समाप्त होगा। आपने अभी तक चेक-इन नहीं किया है"),
                entry(NotificationType.LOCAL_EVENT_CANCELLED, "कार्यक्रम रद्द", "“{eventTitle}” रद्द कर दिया गया"),
                entry(NotificationType.LOCAL_EVENT_POSTPONED, "कार्यक्रम स्थगित", "“{eventTitle}” स्थगित कर दिया गया। नई तारीख अभी तय नहीं है"),
                entry(NotificationType.LOCAL_EVENT_RESCHEDULED, "नई तारीख", "“{eventTitle}” अब {startDate} को होगा"),
                entry(NotificationType.MAP_LIMIT_REACHED, "मैप खोज की सीमा पूरी हुई", "आपने आज की मैप खोजें इस्तेमाल कर ली हैं। ये {resetTime} पर फिर मिलेंगी"),
                entry(NotificationType.MAP_SPAM_ALERT, "संभावित मैप स्पैम", "{userName} के {rejectedCount} मैप अनुरोध आज अस्वीकार किए गए ({operation})"),
                entry(NotificationType.MAP_BUDGET_WARNING, "मैप बजट चेतावनी", "Mapbox {api} ने इस महीने की सीमा का {usedPercent}% इस्तेमाल कर लिया है"),
                entry(NotificationType.MAP_BUDGET_REACHED, "मैप बजट की सीमा पूरी हुई", "Mapbox {api} अपनी सीमा तक पहुँच गया; {resetTime} तक खोज सहेजे गए नतीजों से होगी"),
                entry(NotificationType.PLACE_EDIT_APPROVED, "स्थान अपडेट हुआ", "धन्यवाद! {placeName} की जानकारी अपडेट कर दी गई है"),
                entry(NotificationType.PLACE_EDIT_REJECTED, "सुझाव लागू नहीं हुआ", "{placeName} के लिए आपका सुझाव लागू नहीं किया गया"),
                entry(NotificationType.MEMBER_ACCESS_GRANTED, "अनुरोध स्वीकार हुआ", "{tripName} में शामिल होने का आपका अनुरोध स्वीकार हुआ"),
                entry(NotificationType.MEMBER_JOIN_REJECTED, "अनुरोध अस्वीकार हुआ", "{tripName} में शामिल होने का आपका अनुरोध अस्वीकार हुआ"),
                entry(NotificationType.MEMBER_ROLE_UPDATED, "सदस्य की भूमिका बदली", "{actorName} ने {tripName} में {memberName} की भूमिका {role} की"),
                entry(NotificationType.GUEST_UPDATED, "अतिथि अपडेट हुआ", "{actorName} ने {tripName} में {previousGuestName} का नाम {guestName} किया"),
                entry(NotificationType.CHECKIN_UPDATED, "चेक-इन अपडेट हुआ", "{actorName} ने {tripName} में “{activityName}” का चेक-इन अपडेट किया"),
                entry(NotificationType.NOTE_UPDATED, "नोट अपडेट हुआ", "{actorName} ने {tripName} में “{activityName}” का नोट अपडेट किया"),
                entry(NotificationType.MEMORY_ADDED, "नई यात्रा स्मृति", "{actorName} ने {tripName} में एक स्मृति जोड़ी"),
                entry(NotificationType.MEMORY_DELETED, "यात्रा स्मृति हटाई गई", "{actorName} ने {tripName} से एक स्मृति हटाई"),
                entry(NotificationType.TRIP_BOOK_UPDATED, "यात्रा पुस्तक अपडेट हुई", "{actorName} ने {tripName} की यात्रा पुस्तक अपडेट की"),
                entry(NotificationType.TRIP_CLONED, "यात्रा कॉपी हुई", "{actorName} ने आपकी यात्रा {tripName} कॉपी की"),
                entry(NotificationType.ROUTE_OPTIMIZED, "मार्ग अनुकूलित हुआ", "{tripName} का मार्ग अनुकूलित कर दिया गया है"),
                entry(NotificationType.TRIP_REMINDER, "यात्रा करीब है", "“{tripName}” जल्द शुरू होगी। अपनी यात्रा योजना एक बार देख लें"),
                entry(NotificationType.TRIP_STARTS_IN_ONE_WEEK, "यात्रा में एक सप्ताह बाकी", "“{tripName}” एक सप्ताह में शुरू होगी। अपनी योजना और आरक्षण जाँच लें"),
                entry(NotificationType.TRIP_STARTS_IN_THREE_DAYS, "यात्रा में तीन दिन बाकी", "“{tripName}” तीन दिन में शुरू होगी। टिकट, परिवहन और आरक्षण जाँच लें"),
                entry(NotificationType.TRIP_STARTS_IN_TWO_DAYS, "यात्रा में दो दिन बाकी", "“{tripName}” दो दिन में शुरू होगी। ऑफ़लाइन काम आने वाली जानकारी सहेज लें"),
                entry(NotificationType.TRIP_STARTS_IN_ONE_DAY, "कल है आपकी यात्रा", "“{tripName}” कल शुरू होगी। योजना और ज़रूरी सामान आखिरी बार जाँच लें"),
                entry(NotificationType.TRIP_STARTS_IN_TWO_HOURS, "दो घंटे में प्रस्थान", "“{tripName}” दो घंटे में शुरू होगी। पहला पड़ाव और वहाँ पहुँचने का समय जाँच लें"),
                entry(NotificationType.TRIP_STARTED, "यात्रा शुरू हो गई", "“{tripName}” शुरू हो गई है। आपकी यात्रा सुखद हो!"),
                entry(NotificationType.ITINERARY_ITEM_PREPARATION, "{itemKindLabel} की तैयारी", "“{itemName}” {preparationLeadLabel} में शुरू होगा। {preparationChecklist}"),
                entry(NotificationType.ITINERARY_ITEM_UPCOMING, "15 मिनट बाकी", "अगला {itemKindLabel} “{itemName}” 15 मिनट में शुरू होगा"),
                entry(NotificationType.ITINERARY_ITEM_COMPLETED, "“{itemName}” कैसा रहा?", "आपने “{itemName}” पूरा कर लिया है। एक छोटी समीक्षा लिखकर यादें सहेजें। {nextItemSentence}"),
                entry(NotificationType.TRIP_DAY_ENDED, "दिन {dayNumber} पूरा हुआ", "“{tripName}” का दिन {dayNumber} पूरा हुआ। आज जिन जगहों पर गए, वहाँ चेक-इन करें और एक छोटी समीक्षा लिखें"),
                entry(NotificationType.TRIP_DAY_WEATHER_ALERT, "कल का मौसम पूर्वानुमान: {weatherLabel}", "“{tripName}” का दिन {dayNumber} प्रभावित हो सकता है। ज़रूरत हो तो योजना दोबारा देखें"),
                entry(NotificationType.TRIP_ENDED, "वापसी पर स्वागत है", "“{tripName}” समाप्त हो गई है। आशा है आपकी यात्रा शानदार रही!"),
                entry(NotificationType.TRIP_SUMMARY, "यात्रा की एक झलक", "आपने {placesCount} स्थानों पर {totalExpense} {currency} खर्च किए। {debtSummary}"),
                entry(NotificationType.MARKETPLACE_BOOKING_REQUEST, "नया आरक्षण अनुरोध", "एक अतिथि ने आरक्षण अनुरोध भेजा है। कृपया इसकी जानकारी देखें"),
                entry(NotificationType.MARKETPLACE_BOOKING_CONFIRMED, "आरक्षण की पुष्टि हुई", "आपके आरक्षण की पुष्टि हो गई है। सब तैयार है!"),
                entry(NotificationType.MARKETPLACE_BOOKING_DECLINED, "आरक्षण की पुष्टि नहीं हुई", "आरक्षण अनुरोध की पुष्टि नहीं हो सकी। दूसरे विकल्प देखने के लिए ऐप खोलें"),
                entry(NotificationType.ADMIN_ANNOUNCEMENT, "OnDetour की सूचना", "{body}"),
                entry(NotificationType.ADMIN_MESSAGE, "OnDetour का संदेश", "{body}")
        );
    }

    private Map<NotificationType, NotificationMessage> russian() {
        return Map.ofEntries(
                entry(NotificationType.EXPENSE_ADDED, "Добавлен расход", "{actorName} добавляет «{expenseName}» на сумму {amount} {currency} в «{tripName}»"),
                entry(NotificationType.EXPENSE_UPDATED, "Расход обновлён", "{actorName} обновляет «{expenseName}» в «{tripName}»"),
                entry(NotificationType.EXPENSE_DELETED, "Расход удалён", "{actorName} удаляет «{expenseName}» из «{tripName}»"),
                entry(NotificationType.ACTIVITY_ADDED, "Добавлено событие", "{actorName} добавляет «{activityName}» в «{tripName}»"),
                entry(NotificationType.ACTIVITY_UPDATED, "Событие обновлено", "{actorName} обновляет «{activityName}» в «{tripName}»"),
                entry(NotificationType.ACTIVITY_DELETED, "Событие удалено", "{actorName} удаляет «{activityName}» из «{tripName}»"),
                entry(NotificationType.MEMBER_ADDED, "Новый участник", "{actorName} добавляет {newMemberName} в «{tripName}»"),
                entry(NotificationType.MEMBER_REMOVED, "Участник удалён", "{actorName} удаляет {removedMemberName} из «{tripName}»"),
                entry(NotificationType.MEMBER_ACCEPTED, "Участник присоединился", "{memberName} присоединяется к «{tripName}»"),
                entry(NotificationType.MEMBER_JOINED, "Участник присоединился", "{memberName} присоединяется к «{tripName}»"),
                entry(NotificationType.MEMBER_LEFT, "Участник вышел", "{actorName} покидает «{tripName}»"),
                entry(NotificationType.GUEST_LINKED, "Профиль гостя привязан", "Профиль {guestName} связан с {linkedUserName} в «{tripName}»"),
                entry(NotificationType.TRIP_UPDATED, "Поездка обновлена", "Пользователь {actorName} обновляет поездку «{tripName}»"),
                entry(NotificationType.TRIP_DELETED, "Поездка удалена", "Пользователь {actorName} удаляет поездку «{tripName}»"),
                entry(NotificationType.PAYMENT_MARKED, "Платёж обновлён", "{payerName} обновляет платёж {payeeName} на {amount} {currency} за «{expenseDescription}»"),
                entry(NotificationType.PAYMENT_REMINDER, "Напоминание об оплате", "{actorName} напоминает о платеже {amount} {currency} за «{expenseDescription}»."),
                entry(NotificationType.SETTLEMENT_ADDED, "Платёж записан", "{payerName} заплатил(а) {payeeName} {amount} {currency}."),
                entry(NotificationType.TODO_ASSIGNED, "Вам назначена задача", "{actorName} назначает вам «{todoText}» в «{tripName}»"),
                entry(NotificationType.PAYMENT_ALL_MARKED, "Платежи по расходу обновлены", "{actorName} отмечает платежи за «{expenseDescription}» как {paidStatus}"),
                entry(NotificationType.PAYMENT_TRIP_MARKED, "Платежи поездки обновлены", "{actorName} отмечает платежи в «{tripName}» как {paidStatus}"),
                entry(NotificationType.CHECKIN, "Отметка", "{actorName} отмечается в «{activityName}» во время поездки «{tripName}»"),
                entry(NotificationType.NOTE_ADDED, "Добавлена заметка", "{actorName} добавляет заметку к «{activityName}» в «{tripName}»"),
                entry(NotificationType.NOTE_DELETED, "Заметка удалена", "{actorName} удаляет заметку из «{activityName}» в «{tripName}»"),
                entry(NotificationType.COMMENT_ADDED, "Добавлен комментарий", "{actorName} комментирует «{activityName}» в «{tripName}»"),
                entry(NotificationType.COMMENT_DELETED, "Комментарий удалён", "{actorName} удаляет комментарий из «{activityName}» в «{tripName}»"),
                entry(NotificationType.TRIP_INVITE, "Приглашение в поездку", "Вас пригласили присоединиться к {tripName}"),
                entry(NotificationType.TRIP_INVITE_DECLINED, "Приглашение отклонено", "{memberName} отклоняет приглашение в «{tripName}»"),
                entry(NotificationType.TRIP_INVITE_CANCELLED, "Приглашение отменено", "Ваше приглашение в «{tripName}» отменено"),
                entry(NotificationType.MEMBER_INVITED, "Участник приглашён", "{actorName} приглашает {newMemberName} в «{tripName}»"),
                entry(NotificationType.MEMBER_JOIN_REQUESTED, "Запрос на участие", "{memberName} просит присоединиться к «{tripName}»"),
                entry(NotificationType.MEETUP_APPROVED, "Встреча одобрена", "«{meetupTitle}» теперь на карте"),
                entry(NotificationType.MEETUP_REJECTED, "Встреча не одобрена", "«{meetupTitle}» не прошла проверку. {reason}"),
                entry(NotificationType.MEETUP_REMOVED, "Встреча удалена", "«{meetupTitle}» удалена с карты. {reason}"),
                entry(NotificationType.MEETUP_JOIN_REQUESTED, "Новая заявка на участие", "{userName} хочет присоединиться к «{meetupTitle}»"),
                entry(NotificationType.MEETUP_PARTICIPANT_JOINED, "Новый участник", "{userName} присоединяется к «{meetupTitle}»"),
                entry(NotificationType.MEETUP_REQUEST_ACCEPTED, "Заявка принята", "{hostName} принимает вашу заявку на участие в «{meetupTitle}»"),
                entry(NotificationType.MEETUP_REQUEST_DECLINED, "Заявка не принята", "{hostName} не может принять вашу заявку на участие в «{meetupTitle}»"),
                entry(NotificationType.MEETUP_CANCELLED, "Встреча отменена", "«{meetupTitle}» ({hostName}) отменена. {reason}"),
                entry(NotificationType.MEETUP_INVITED, "Вас пригласили", "{hostName} приглашает вас на «{meetupTitle}»"),
                entry(NotificationType.LOCAL_EVENT_TOMORROW, "Событие завтра", "«{eventTitle}» состоится завтра. Нажмите, чтобы увидеть время и место"),
                entry(NotificationType.LOCAL_EVENT_STARTING_SOON, "Скоро начало", "«{eventTitle}» начнётся в {startTime}"),
                entry(NotificationType.LOCAL_EVENT_OPENING_TODAY, "Открытие сегодня", "«{eventTitle}» открывается сегодня"),
                entry(NotificationType.LOCAL_EVENT_ENDING_SOON, "Скоро закончится", "«{eventTitle}» закрывается {endDate}. Вы ещё не отметились"),
                entry(NotificationType.LOCAL_EVENT_CANCELLED, "Событие отменено", "«{eventTitle}» отменено"),
                entry(NotificationType.LOCAL_EVENT_POSTPONED, "Событие отложено", "«{eventTitle}» отложено. Новой даты пока нет"),
                entry(NotificationType.LOCAL_EVENT_RESCHEDULED, "Событие перенесено", "«{eventTitle}» перенесено на {startDate}"),
                entry(NotificationType.MAP_LIMIT_REACHED, "Лимит поиска по карте исчерпан", "Вы использовали все поиски по карте на сегодня. Они снова будут доступны в {resetTime}"),
                entry(NotificationType.MAP_SPAM_ALERT, "Возможный спам карты", "Пользователю {userName} сегодня отказано в {rejectedCount} запросах к карте ({operation})"),
                entry(NotificationType.MAP_BUDGET_WARNING, "Предупреждение о бюджете карты", "Mapbox {api} израсходовал {usedPercent}% лимита этого месяца"),
                entry(NotificationType.MAP_BUDGET_REACHED, "Бюджет карты исчерпан", "Mapbox {api} достиг лимита; до {resetTime} поиск использует сохранённые результаты"),
                entry(NotificationType.PLACE_EDIT_APPROVED, "Место обновлено", "Спасибо! Информация о «{placeName}» обновлена"),
                entry(NotificationType.PLACE_EDIT_REJECTED, "Предложение не принято", "Ваше предложение для «{placeName}» не было применено"),
                entry(NotificationType.MEMBER_ACCESS_GRANTED, "Запрос принят", "Ваш запрос на участие в «{tripName}» принят"),
                entry(NotificationType.MEMBER_JOIN_REJECTED, "Запрос отклонён", "Ваш запрос на участие в «{tripName}» отклонён"),
                entry(NotificationType.MEMBER_ROLE_UPDATED, "Роль участника изменена", "{actorName} меняет роль {memberName} в «{tripName}» на {role}"),
                entry(NotificationType.GUEST_UPDATED, "Данные гостя изменены", "{actorName} переименовывает {previousGuestName} в {guestName} в «{tripName}»"),
                entry(NotificationType.CHECKIN_UPDATED, "Отметка обновлена", "{actorName} обновляет отметку в «{activityName}» во время «{tripName}»"),
                entry(NotificationType.NOTE_UPDATED, "Заметка обновлена", "{actorName} обновляет заметку к «{activityName}» в «{tripName}»"),
                entry(NotificationType.MEMORY_ADDED, "Новое воспоминание", "{actorName} добавляет воспоминание в «{tripName}»"),
                entry(NotificationType.MEMORY_DELETED, "Воспоминание удалено", "{actorName} удаляет воспоминание из «{tripName}»"),
                entry(NotificationType.TRIP_BOOK_UPDATED, "Книга путешествия обновлена", "{actorName} обновляет книгу путешествия «{tripName}»"),
                entry(NotificationType.TRIP_CLONED, "Поездка скопирована", "{actorName} копирует вашу поездку «{tripName}»"),
                entry(NotificationType.ROUTE_OPTIMIZED, "Маршрут оптимизирован", "Маршрут для {tripName} оптимизирован"),
                entry(NotificationType.TRIP_REMINDER, "Поездка уже близко", "«{tripName}» скоро начнётся. Загляните в план поездки"),
                entry(NotificationType.TRIP_STARTS_IN_ONE_WEEK, "До поездки одна неделя", "«{tripName}» начнётся через неделю. Проверьте маршрут и бронирования"),
                entry(NotificationType.TRIP_STARTS_IN_THREE_DAYS, "До поездки три дня", "«{tripName}» начнётся через три дня. Проверьте билеты, транспорт и бронирования"),
                entry(NotificationType.TRIP_STARTS_IN_TWO_DAYS, "До поездки два дня", "«{tripName}» начнётся через два дня. Сохраните всё, что понадобится без интернета"),
                entry(NotificationType.TRIP_STARTS_IN_ONE_DAY, "Отправление уже завтра", "«{tripName}» начнётся завтра. Ещё раз проверьте план и всё необходимое"),
                entry(NotificationType.TRIP_STARTS_IN_TWO_HOURS, "Отправление через два часа", "«{tripName}» начнётся через два часа. Проверьте первую остановку и время в пути"),
                entry(NotificationType.TRIP_STARTED, "Поездка началась", "«{tripName}» уже началась. Желаем отличного путешествия!"),
                entry(NotificationType.ITINERARY_ITEM_PREPARATION, "Подготовьтесь: {itemKindLabel}", "«{itemName}» начнётся через {preparationLeadLabel}. {preparationChecklist}"),
                entry(NotificationType.ITINERARY_ITEM_UPCOMING, "Осталось 15 минут", "Следующий пункт — {itemKindLabel} «{itemName}». Начало через 15 минут"),
                entry(NotificationType.ITINERARY_ITEM_COMPLETED, "Как вам «{itemName}»?", "«{itemName}» завершено. Оставьте короткий отзыв, чтобы сохранить впечатления. {nextItemSentence}"),
                entry(NotificationType.TRIP_DAY_ENDED, "День {dayNumber} позади", "День {dayNumber} поездки «{tripName}» завершён. Отметьтесь в местах, где побывали, и оставьте короткий отзыв"),
                entry(NotificationType.TRIP_DAY_WEATHER_ALERT, "Прогноз на завтра: {weatherLabel}", "Погода может повлиять на день {dayNumber} поездки «{tripName}». Проверьте план, если захотите его изменить"),
                entry(NotificationType.TRIP_ENDED, "С возвращением", "«{tripName}» завершилась. Надеемся, поездка была замечательной!"),
                entry(NotificationType.TRIP_SUMMARY, "Поездка в цифрах", "Вы потратили {totalExpense} {currency} в {placesCount} местах. {debtSummary}"),
                entry(NotificationType.MARKETPLACE_BOOKING_REQUEST, "Новый запрос на бронирование", "Гость отправил запрос на бронирование. Проверьте детали"),
                entry(NotificationType.MARKETPLACE_BOOKING_CONFIRMED, "Бронирование подтверждено", "Бронирование подтверждено. Всё готово!"),
                entry(NotificationType.MARKETPLACE_BOOKING_DECLINED, "Бронирование не подтверждено", "Не удалось подтвердить запрос. Откройте приложение, чтобы посмотреть другие варианты"),
                entry(NotificationType.ADMIN_ANNOUNCEMENT, "Объявление OnDetour", "{body}"),
                entry(NotificationType.ADMIN_MESSAGE, "Сообщение от OnDetour", "{body}")
        );
    }

    private Map<NotificationType, NotificationMessage> thai() {
        return Map.ofEntries(
                entry(NotificationType.EXPENSE_ADDED, "เพิ่มค่าใช้จ่ายแล้ว", "{actorName} เพิ่ม “{expenseName}” {amount} {currency} ใน {tripName}"),
                entry(NotificationType.EXPENSE_UPDATED, "อัปเดตค่าใช้จ่ายแล้ว", "{actorName} อัปเดต “{expenseName}” ใน {tripName}"),
                entry(NotificationType.EXPENSE_DELETED, "ลบค่าใช้จ่ายแล้ว", "{actorName} ลบ “{expenseName}” ออกจาก {tripName}"),
                entry(NotificationType.ACTIVITY_ADDED, "เพิ่มกิจกรรมแล้ว", "{actorName} เพิ่ม “{activityName}” ใน {tripName}"),
                entry(NotificationType.ACTIVITY_UPDATED, "อัปเดตกิจกรรมแล้ว", "{actorName} อัปเดต “{activityName}” ใน {tripName}"),
                entry(NotificationType.ACTIVITY_DELETED, "ลบกิจกรรมแล้ว", "{actorName} ลบ “{activityName}” ออกจาก {tripName}"),
                entry(NotificationType.MEMBER_ADDED, "สมาชิกใหม่", "{actorName} เพิ่ม {newMemberName} ใน {tripName}"),
                entry(NotificationType.MEMBER_REMOVED, "ลบสมาชิกแล้ว", "{actorName} ลบ {removedMemberName} ออกจาก {tripName}"),
                entry(NotificationType.MEMBER_ACCEPTED, "สมาชิกเข้าร่วมแล้ว", "{memberName} เข้าร่วม {tripName}"),
                entry(NotificationType.MEMBER_JOINED, "สมาชิกเข้าร่วมแล้ว", "{memberName} เข้าร่วม {tripName}"),
                entry(NotificationType.MEMBER_LEFT, "สมาชิกออกแล้ว", "{actorName} ออกจาก {tripName}"),
                entry(NotificationType.GUEST_LINKED, "เชื่อมโยงผู้เข้าร่วมแล้ว", "เชื่อมโยง {guestName} กับ {linkedUserName} ใน {tripName} แล้ว"),
                entry(NotificationType.TRIP_UPDATED, "อัปเดตทริปแล้ว", "{actorName} อัปเดตทริป {tripName}"),
                entry(NotificationType.TRIP_DELETED, "ลบทริปแล้ว", "{actorName} ลบทริป {tripName}"),
                entry(NotificationType.PAYMENT_MARKED, "อัปเดตการชำระเงินแล้ว", "{payerName} อัปเดตยอด {amount} {currency} ของ {payeeName} สำหรับ “{expenseDescription}”"),
                entry(NotificationType.PAYMENT_REMINDER, "แจ้งเตือนการชำระเงิน", "{actorName} เตือนคุณเรื่องยอด {amount} {currency} สำหรับ “{expenseDescription}”."),
                entry(NotificationType.SETTLEMENT_ADDED, "บันทึกการชำระเงินแล้ว", "{payerName} จ่ายให้ {payeeName} {amount} {currency}."),
                entry(NotificationType.TODO_ASSIGNED, "คุณได้รับมอบหมายงาน", "{actorName} มอบหมาย “{todoText}” ให้คุณใน “{tripName}”"),
                entry(NotificationType.PAYMENT_ALL_MARKED, "อัปเดตการชำระค่าใช้จ่ายแล้ว", "{actorName} ทำเครื่องหมายการชำระ “{expenseDescription}” เป็น {paidStatus}"),
                entry(NotificationType.PAYMENT_TRIP_MARKED, "อัปเดตการชำระของทริปแล้ว", "{actorName} ทำเครื่องหมายการชำระใน {tripName} เป็น {paidStatus}"),
                entry(NotificationType.CHECKIN, "เช็กอิน", "{actorName} เช็กอินที่ “{activityName}” ใน {tripName}"),
                entry(NotificationType.NOTE_ADDED, "เพิ่มโน้ตแล้ว", "{actorName} เพิ่มโน้ตสำหรับ “{activityName}” ใน “{tripName}”"),
                entry(NotificationType.NOTE_DELETED, "ลบโน้ตแล้ว", "{actorName} ลบโน้ตของ “{activityName}” ออกจาก “{tripName}”"),
                entry(NotificationType.COMMENT_ADDED, "เพิ่มความคิดเห็นแล้ว", "{actorName} แสดงความคิดเห็นที่ “{activityName}” ใน {tripName}"),
                entry(NotificationType.COMMENT_DELETED, "ลบความคิดเห็นแล้ว", "{actorName} ลบความคิดเห็นจาก “{activityName}” ใน {tripName}"),
                entry(NotificationType.TRIP_INVITE, "คำเชิญเข้าร่วมทริป", "คุณได้รับเชิญให้เข้าร่วม {tripName}"),
                entry(NotificationType.TRIP_INVITE_DECLINED, "ปฏิเสธคำเชิญแล้ว", "{memberName} ปฏิเสธคำเชิญเข้าร่วม {tripName}"),
                entry(NotificationType.TRIP_INVITE_CANCELLED, "ยกเลิกคำเชิญแล้ว", "คำเชิญของคุณสำหรับ {tripName} ถูกยกเลิกแล้ว"),
                entry(NotificationType.MEMBER_INVITED, "เชิญสมาชิกแล้ว", "{actorName} เชิญ {newMemberName} เข้าร่วม {tripName}"),
                entry(NotificationType.MEMBER_JOIN_REQUESTED, "คำขอเข้าร่วม", "{memberName} ขอเข้าร่วม {tripName}"),
                entry(NotificationType.MEETUP_APPROVED, "อนุมัติกิจกรรมแล้ว", "“{meetupTitle}” แสดงบนแผนที่แล้ว"),
                entry(NotificationType.MEETUP_REJECTED, "กิจกรรมไม่ผ่านการอนุมัติ", "“{meetupTitle}” ไม่ผ่านการอนุมัติ {reason}"),
                entry(NotificationType.MEETUP_REMOVED, "กิจกรรมถูกนำออก", "“{meetupTitle}” ถูกนำออกจากแผนที่ {reason}"),
                entry(NotificationType.MEETUP_JOIN_REQUESTED, "คำขอเข้าร่วมใหม่", "{userName} ขอเข้าร่วม “{meetupTitle}”"),
                entry(NotificationType.MEETUP_PARTICIPANT_JOINED, "มีผู้เข้าร่วมใหม่", "{userName} เข้าร่วม “{meetupTitle}” แล้ว"),
                entry(NotificationType.MEETUP_REQUEST_ACCEPTED, "คำขอได้รับการตอบรับ", "{hostName} ตอบรับคำขอเข้าร่วม “{meetupTitle}” ของคุณแล้ว"),
                entry(NotificationType.MEETUP_REQUEST_DECLINED, "คำขอไม่ได้รับการตอบรับ", "{hostName} ไม่สามารถตอบรับคำขอเข้าร่วม “{meetupTitle}” ของคุณได้"),
                entry(NotificationType.MEETUP_CANCELLED, "กิจกรรมถูกยกเลิก", "“{meetupTitle}” ของ {hostName} ถูกยกเลิกแล้ว {reason}"),
                entry(NotificationType.MEETUP_INVITED, "คุณได้รับคำเชิญ", "{hostName} เชิญคุณเข้าร่วม “{meetupTitle}”"),
                entry(NotificationType.LOCAL_EVENT_TOMORROW, "งานพรุ่งนี้", "“{eventTitle}” จัดพรุ่งนี้ แตะเพื่อดูเวลาและสถานที่"),
                entry(NotificationType.LOCAL_EVENT_STARTING_SOON, "ใกล้เริ่มแล้ว", "“{eventTitle}” เริ่มเวลา {startTime}"),
                entry(NotificationType.LOCAL_EVENT_OPENING_TODAY, "เปิดวันนี้", "“{eventTitle}” เปิดวันนี้"),
                entry(NotificationType.LOCAL_EVENT_ENDING_SOON, "ใกล้จบแล้ว", "“{eventTitle}” จะจบวันที่ {endDate} คุณยังไม่ได้เช็กอิน"),
                entry(NotificationType.LOCAL_EVENT_CANCELLED, "งานถูกยกเลิก", "“{eventTitle}” ถูกยกเลิกแล้ว"),
                entry(NotificationType.LOCAL_EVENT_POSTPONED, "งานเลื่อนออกไป", "“{eventTitle}” ถูกเลื่อนออกไป ยังไม่มีวันใหม่"),
                entry(NotificationType.LOCAL_EVENT_RESCHEDULED, "งานเปลี่ยนวัน", "“{eventTitle}” ย้ายไปวันที่ {startDate}"),
                entry(NotificationType.MAP_LIMIT_REACHED, "ใช้การค้นหาแผนที่ครบแล้ว", "คุณใช้การค้นหาแผนที่ของวันนี้ครบแล้ว จะใช้ได้อีกครั้งเวลา {resetTime}"),
                entry(NotificationType.MAP_SPAM_ALERT, "สงสัยว่ามีการสแปมแผนที่", "คำขอแผนที่ของ {userName} ถูกปฏิเสธ {rejectedCount} ครั้งในวันนี้ ({operation})"),
                entry(NotificationType.MAP_BUDGET_WARNING, "คำเตือนงบประมาณแผนที่", "Mapbox {api} ใช้ไปแล้ว {usedPercent}% ของวงเงินเดือนนี้"),
                entry(NotificationType.MAP_BUDGET_REACHED, "งบประมาณแผนที่ถึงขีดจำกัดแล้ว", "Mapbox {api} ถึงขีดจำกัดแล้ว การค้นหาจะใช้ผลลัพธ์ที่บันทึกไว้จนถึง {resetTime}"),
                entry(NotificationType.PLACE_EDIT_APPROVED, "อัปเดตสถานที่แล้ว", "ขอบคุณ! ข้อมูลของ {placeName} ได้รับการอัปเดตแล้ว"),
                entry(NotificationType.PLACE_EDIT_REJECTED, "ยังไม่ได้ใช้คำแนะนำ", "คำแนะนำของคุณสำหรับ {placeName} ยังไม่ได้รับการนำไปใช้"),
                entry(NotificationType.MEMBER_ACCESS_GRANTED, "อนุมัติคำขอแล้ว", "คำขอเข้าร่วม {tripName} ของคุณได้รับการอนุมัติแล้ว"),
                entry(NotificationType.MEMBER_JOIN_REJECTED, "ไม่อนุมัติคำขอ", "คำขอเข้าร่วม {tripName} ของคุณถูกปฏิเสธ"),
                entry(NotificationType.MEMBER_ROLE_UPDATED, "อัปเดตบทบาทสมาชิกแล้ว", "{actorName} เปลี่ยนบทบาทของ {memberName} ใน {tripName} เป็น {role}"),
                entry(NotificationType.GUEST_UPDATED, "อัปเดตผู้เข้าร่วมแล้ว", "{actorName} เปลี่ยนชื่อ {previousGuestName} เป็น {guestName} ใน {tripName}"),
                entry(NotificationType.CHECKIN_UPDATED, "อัปเดตเช็กอินแล้ว", "{actorName} อัปเดตเช็กอินที่ “{activityName}” ใน {tripName}"),
                entry(NotificationType.NOTE_UPDATED, "อัปเดตโน้ตแล้ว", "{actorName} อัปเดตโน้ตของ “{activityName}” ใน {tripName}"),
                entry(NotificationType.MEMORY_ADDED, "เพิ่มความทรงจำแล้ว", "{actorName} เพิ่มความทรงจำใน {tripName}"),
                entry(NotificationType.MEMORY_DELETED, "ลบความทรงจำแล้ว", "{actorName} ลบความทรงจำออกจาก {tripName}"),
                entry(NotificationType.TRIP_BOOK_UPDATED, "อัปเดตสมุดทริปแล้ว", "{actorName} อัปเดตสมุดทริปของ {tripName}"),
                entry(NotificationType.TRIP_CLONED, "ทริปถูกคัดลอก", "{actorName} คัดลอกทริป {tripName} ของคุณ"),
                entry(NotificationType.ROUTE_OPTIMIZED, "ปรับเส้นทางแล้ว", "ปรับเส้นทางสำหรับ {tripName} เรียบร้อยแล้ว"),
                entry(NotificationType.TRIP_REMINDER, "ทริปใกล้เข้ามาแล้ว", "“{tripName}” กำลังจะเริ่ม ลองทบทวนแผนการเดินทางสักครู่นะ"),
                entry(NotificationType.TRIP_STARTS_IN_ONE_WEEK, "เหลืออีกหนึ่งสัปดาห์", "“{tripName}” จะเริ่มในอีกหนึ่งสัปดาห์ อย่าลืมตรวจสอบแผนและการจอง"),
                entry(NotificationType.TRIP_STARTS_IN_THREE_DAYS, "เหลืออีกสามวัน", "“{tripName}” จะเริ่มในอีกสามวัน ตรวจสอบตั๋ว การเดินทาง และการจองให้พร้อมนะ"),
                entry(NotificationType.TRIP_STARTS_IN_TWO_DAYS, "เหลืออีกสองวัน", "“{tripName}” จะเริ่มในอีกสองวัน บันทึกข้อมูลที่อาจต้องใช้ตอนออฟไลน์ไว้ล่วงหน้า"),
                entry(NotificationType.TRIP_STARTS_IN_ONE_DAY, "พรุ่งนี้ออกเดินทาง", "“{tripName}” จะเริ่มพรุ่งนี้ ตรวจสอบแผนและของจำเป็นอีกครั้งนะ"),
                entry(NotificationType.TRIP_STARTS_IN_TWO_HOURS, "ออกเดินทางในอีกสองชั่วโมง", "“{tripName}” จะเริ่มในอีกสองชั่วโมง ตรวจสอบจุดหมายแรกและเผื่อเวลาเดินทางด้วยนะ"),
                entry(NotificationType.TRIP_STARTED, "ทริปเริ่มแล้ว", "“{tripName}” เริ่มแล้ว ขอให้เป็นทริปที่ยอดเยี่ยม!"),
                entry(NotificationType.ITINERARY_ITEM_PREPARATION, "เตรียมตัวสำหรับ{itemKindLabel}", "“{itemName}” จะเริ่มในอีก {preparationLeadLabel} {preparationChecklist}"),
                entry(NotificationType.ITINERARY_ITEM_UPCOMING, "เหลืออีก 15 นาที", "{itemKindLabel}ถัดไป “{itemName}” จะเริ่มในอีก 15 นาที"),
                entry(NotificationType.ITINERARY_ITEM_COMPLETED, "“{itemName}” เป็นอย่างไรบ้าง?", "คุณเสร็จสิ้น “{itemName}” แล้ว เขียนรีวิวสั้น ๆ เพื่อเก็บความทรงจำได้นะ {nextItemSentence}"),
                entry(NotificationType.TRIP_DAY_ENDED, "จบวันที่ {dayNumber} แล้ว", "วันที่ {dayNumber} ของ “{tripName}” จบแล้ว เช็กอินสถานที่ที่ไปมาวันนี้และเขียนรีวิวสั้น ๆ ไว้นะ"),
                entry(NotificationType.TRIP_DAY_WEATHER_ALERT, "พยากรณ์อากาศพรุ่งนี้: {weatherLabel}", "วันที่ {dayNumber} ของ “{tripName}” อาจได้รับผลกระทบ ลองดูแผนอีกครั้งหากต้องการเปลี่ยน"),
                entry(NotificationType.TRIP_ENDED, "ยินดีต้อนรับกลับ", "“{tripName}” สิ้นสุดแล้ว หวังว่าคุณจะมีทริปที่ยอดเยี่ยม!"),
                entry(NotificationType.TRIP_SUMMARY, "ภาพรวมทริป", "คุณใช้จ่าย {totalExpense} {currency} ใน {placesCount} สถานที่ {debtSummary}"),
                entry(NotificationType.MARKETPLACE_BOOKING_REQUEST, "คำขอจองใหม่", "มีคำขอจองใหม่จากผู้เข้าพัก โปรดตรวจสอบรายละเอียด"),
                entry(NotificationType.MARKETPLACE_BOOKING_CONFIRMED, "ยืนยันการจองแล้ว", "การจองของคุณได้รับการยืนยันแล้ว ทุกอย่างพร้อม!"),
                entry(NotificationType.MARKETPLACE_BOOKING_DECLINED, "ยังยืนยันการจองไม่ได้", "ไม่สามารถยืนยันคำขอจองได้ เปิดแอปเพื่อดูตัวเลือกอื่น"),
                entry(NotificationType.ADMIN_ANNOUNCEMENT, "ประกาศจาก OnDetour", "{body}"),
                entry(NotificationType.ADMIN_MESSAGE, "ข้อความจาก OnDetour", "{body}")
        );
    }

    private Map<NotificationType, NotificationMessage> traditionalChinese() {
        return Map.ofEntries(
                entry(NotificationType.EXPENSE_ADDED, "新增支出", "{actorName} 在「{tripName}」新增「{expenseName}」{amount} {currency}"),
                entry(NotificationType.EXPENSE_UPDATED, "更新支出", "{actorName} 更新了「{tripName}」的「{expenseName}」"),
                entry(NotificationType.EXPENSE_DELETED, "刪除支出", "{actorName} 從「{tripName}」刪除了「{expenseName}」"),
                entry(NotificationType.ACTIVITY_ADDED, "新增活動", "{actorName} 在「{tripName}」新增了「{activityName}」"),
                entry(NotificationType.ACTIVITY_UPDATED, "更新活動", "{actorName} 更新了「{tripName}」的「{activityName}」"),
                entry(NotificationType.ACTIVITY_DELETED, "刪除活動", "{actorName} 從「{tripName}」刪除了「{activityName}」"),
                entry(NotificationType.MEMBER_ADDED, "新增成員", "{actorName} 將 {newMemberName} 加入「{tripName}」"),
                entry(NotificationType.MEMBER_REMOVED, "移除成員", "{actorName} 將 {removedMemberName} 移出「{tripName}」"),
                entry(NotificationType.MEMBER_ACCEPTED, "成員已加入", "{memberName} 已加入「{tripName}」"),
                entry(NotificationType.MEMBER_JOINED, "成員已加入", "{memberName} 已加入「{tripName}」"),
                entry(NotificationType.MEMBER_LEFT, "成員已離開", "{actorName} 已離開「{tripName}」"),
                entry(NotificationType.GUEST_LINKED, "訪客已連結", "「{tripName}」中的 {guestName} 已連結至 {linkedUserName}"),
                entry(NotificationType.TRIP_UPDATED, "更新旅程", "{actorName} 更新了旅程「{tripName}」"),
                entry(NotificationType.TRIP_DELETED, "刪除旅程", "{actorName} 刪除了旅程「{tripName}」"),
                entry(NotificationType.PAYMENT_MARKED, "更新付款", "{payerName} 更新了「{expenseDescription}」中 {payeeName} 的付款（{amount} {currency}）"),
                entry(NotificationType.PAYMENT_REMINDER, "付款提醒", "{actorName} 提醒您支付「{expenseDescription}」的 {amount} {currency}。"),
                entry(NotificationType.SETTLEMENT_ADDED, "已記錄付款", "{payerName} 已付給 {payeeName} {amount} {currency}。"),
                entry(NotificationType.TODO_ASSIGNED, "你被指派了一項待辦", "{actorName} 在「{tripName}」把「{todoText}」指派給你"),
                entry(NotificationType.PAYMENT_ALL_MARKED, "更新支出付款", "{actorName} 將「{expenseDescription}」的付款標記為{paidStatus}"),
                entry(NotificationType.PAYMENT_TRIP_MARKED, "更新旅程付款", "{actorName} 將「{tripName}」的付款標記為{paidStatus}"),
                entry(NotificationType.CHECKIN, "打卡", "{actorName} 在「{tripName}」的「{activityName}」打卡"),
                entry(NotificationType.NOTE_ADDED, "新增筆記", "{actorName} 在「{tripName}」的「{activityName}」新增了筆記"),
                entry(NotificationType.NOTE_DELETED, "刪除筆記", "{actorName} 從「{tripName}」的「{activityName}」刪除了筆記"),
                entry(NotificationType.COMMENT_ADDED, "新增留言", "{actorName} 在「{tripName}」的「{activityName}」留言"),
                entry(NotificationType.COMMENT_DELETED, "刪除留言", "{actorName} 從「{tripName}」的「{activityName}」刪除了留言"),
                entry(NotificationType.TRIP_INVITE, "旅程邀請", "你已受邀加入「{tripName}」"),
                entry(NotificationType.TRIP_INVITE_DECLINED, "邀請已被婉拒", "{memberName} 婉拒了「{tripName}」的邀請"),
                entry(NotificationType.TRIP_INVITE_CANCELLED, "邀請已取消", "你加入「{tripName}」的邀請已取消"),
                entry(NotificationType.MEMBER_INVITED, "已邀請成員", "{actorName} 邀請 {newMemberName} 加入「{tripName}」"),
                entry(NotificationType.MEMBER_JOIN_REQUESTED, "加入申請", "{memberName} 申請加入「{tripName}」"),
                entry(NotificationType.MEETUP_APPROVED, "活動已核准", "「{meetupTitle}」已顯示在地圖上"),
                entry(NotificationType.MEETUP_REJECTED, "活動未獲核准", "「{meetupTitle}」未獲核准。{reason}"),
                entry(NotificationType.MEETUP_REMOVED, "活動已移除", "「{meetupTitle}」已從地圖上移除。{reason}"),
                entry(NotificationType.MEETUP_JOIN_REQUESTED, "新的加入申請", "{userName} 申請加入「{meetupTitle}」"),
                entry(NotificationType.MEETUP_PARTICIPANT_JOINED, "有人加入了", "{userName} 已加入「{meetupTitle}」"),
                entry(NotificationType.MEETUP_REQUEST_ACCEPTED, "申請已獲接受", "{hostName} 已接受你加入「{meetupTitle}」的申請"),
                entry(NotificationType.MEETUP_REQUEST_DECLINED, "申請未獲接受", "{hostName} 無法接受你加入「{meetupTitle}」的申請"),
                entry(NotificationType.MEETUP_CANCELLED, "活動已取消", "{hostName} 的「{meetupTitle}」已取消。{reason}"),
                entry(NotificationType.MEETUP_INVITED, "你收到邀請", "{hostName} 邀請你參加「{meetupTitle}」"),
                entry(NotificationType.LOCAL_EVENT_TOMORROW, "明天的活動", "「{eventTitle}」明天舉行。點一下查看時間和地點"),
                entry(NotificationType.LOCAL_EVENT_STARTING_SOON, "即將開始", "「{eventTitle}」將於 {startTime} 開始"),
                entry(NotificationType.LOCAL_EVENT_OPENING_TODAY, "今天開幕", "「{eventTitle}」今天開始"),
                entry(NotificationType.LOCAL_EVENT_ENDING_SOON, "即將結束", "「{eventTitle}」將於 {endDate} 結束。你還沒有打卡"),
                entry(NotificationType.LOCAL_EVENT_CANCELLED, "活動已取消", "「{eventTitle}」已取消"),
                entry(NotificationType.LOCAL_EVENT_POSTPONED, "活動延期", "「{eventTitle}」已延期，新日期未定"),
                entry(NotificationType.LOCAL_EVENT_RESCHEDULED, "活動改期", "「{eventTitle}」改到 {startDate}"),
                entry(NotificationType.MAP_LIMIT_REACHED, "地圖搜尋已達上限", "你今天的地圖搜尋次數已用完，將於 {resetTime} 恢復"),
                entry(NotificationType.MAP_SPAM_ALERT, "疑似地圖濫用", "{userName} 今天有 {rejectedCount} 次地圖請求被拒絕（{operation}）"),
                entry(NotificationType.MAP_BUDGET_WARNING, "地圖預算警告", "Mapbox {api} 已用掉本月上限的 {usedPercent}%"),
                entry(NotificationType.MAP_BUDGET_REACHED, "地圖預算已達上限", "Mapbox {api} 已達上限，{resetTime} 前搜尋將使用已儲存的結果"),
                entry(NotificationType.PLACE_EDIT_APPROVED, "地點資訊已更新", "謝謝！{placeName} 的資訊已更新"),
                entry(NotificationType.PLACE_EDIT_REJECTED, "建議未被採用", "你對 {placeName} 的建議未被採用"),
                entry(NotificationType.MEMBER_ACCESS_GRANTED, "申請已核准", "你加入「{tripName}」的申請已核准"),
                entry(NotificationType.MEMBER_JOIN_REJECTED, "申請未核准", "你加入「{tripName}」的申請已被拒絕"),
                entry(NotificationType.MEMBER_ROLE_UPDATED, "成員角色已更新", "{actorName} 將「{tripName}」中 {memberName} 的角色改為 {role}"),
                entry(NotificationType.GUEST_UPDATED, "訪客資料已更新", "{actorName} 在「{tripName}」將 {previousGuestName} 改名為 {guestName}"),
                entry(NotificationType.CHECKIN_UPDATED, "打卡已更新", "{actorName} 更新了「{tripName}」中「{activityName}」的打卡"),
                entry(NotificationType.NOTE_UPDATED, "筆記已更新", "{actorName} 更新了「{tripName}」中「{activityName}」的筆記"),
                entry(NotificationType.MEMORY_ADDED, "新增旅程回憶", "{actorName} 在「{tripName}」新增了一段回憶"),
                entry(NotificationType.MEMORY_DELETED, "旅程回憶已刪除", "{actorName} 從「{tripName}」刪除了一段回憶"),
                entry(NotificationType.TRIP_BOOK_UPDATED, "旅遊手冊已更新", "{actorName} 更新了「{tripName}」的旅遊手冊"),
                entry(NotificationType.TRIP_CLONED, "旅程已被複製", "{actorName} 複製了你的旅程「{tripName}」"),
                entry(NotificationType.ROUTE_OPTIMIZED, "路線已最佳化", "「{tripName}」的路線已完成最佳化"),
                entry(NotificationType.TRIP_REMINDER, "旅程快到了", "「{tripName}」即將開始，花點時間再確認一次行程吧"),
                entry(NotificationType.TRIP_STARTS_IN_ONE_WEEK, "出發倒數一週", "「{tripName}」將在一週後開始，記得確認行程與預訂"),
                entry(NotificationType.TRIP_STARTS_IN_THREE_DAYS, "出發倒數三天", "「{tripName}」將在三天後開始，請確認票券、交通與預訂"),
                entry(NotificationType.TRIP_STARTS_IN_TWO_DAYS, "出發倒數兩天", "「{tripName}」將在兩天後開始，先儲存離線時可能需要的資料"),
                entry(NotificationType.TRIP_STARTS_IN_ONE_DAY, "明天出發", "「{tripName}」明天開始，最後確認一次行程與必備物品吧"),
                entry(NotificationType.TRIP_STARTS_IN_TWO_HOURS, "兩小時後出發", "「{tripName}」將在兩小時後開始，請確認第一站並預留交通時間"),
                entry(NotificationType.TRIP_STARTED, "旅程開始了", "「{tripName}」已經開始，祝你旅途愉快！"),
                entry(NotificationType.ITINERARY_ITEM_PREPARATION, "準備{itemKindLabel}", "「{itemName}」將在 {preparationLeadLabel}後開始。{preparationChecklist}"),
                entry(NotificationType.ITINERARY_ITEM_UPCOMING, "還有 15 分鐘", "下一個{itemKindLabel}「{itemName}」將在 15 分鐘後開始"),
                entry(NotificationType.ITINERARY_ITEM_COMPLETED, "「{itemName}」體驗如何？", "你已完成「{itemName}」。寫下簡短評價，留住這段回憶吧。{nextItemSentence}"),
                entry(NotificationType.TRIP_DAY_ENDED, "第 {dayNumber} 天結束了", "「{tripName}」的第 {dayNumber} 天結束了。到今天去過的地方打卡，寫下簡短評價吧"),
                entry(NotificationType.TRIP_DAY_WEATHER_ALERT, "明天天氣預報：{weatherLabel}", "「{tripName}」的第 {dayNumber} 天可能受到影響。需要的話可以重新檢查行程"),
                entry(NotificationType.TRIP_ENDED, "歡迎回來", "「{tripName}」已結束，希望你度過了一段美好的旅程！"),
                entry(NotificationType.TRIP_SUMMARY, "旅程回顧", "你在 {placesCount} 個地點共花費 {totalExpense} {currency}。{debtSummary}"),
                entry(NotificationType.MARKETPLACE_BOOKING_REQUEST, "新的預訂請求", "旅客送出了一筆預訂請求，請查看詳情"),
                entry(NotificationType.MARKETPLACE_BOOKING_CONFIRMED, "預訂已確認", "你的預訂已確認，一切都準備好了！"),
                entry(NotificationType.MARKETPLACE_BOOKING_DECLINED, "預訂尚未確認", "目前無法確認這筆預訂，請開啟應用程式查看其他選項"),
                entry(NotificationType.ADMIN_ANNOUNCEMENT, "OnDetour 公告", "{body}"),
                entry(NotificationType.ADMIN_MESSAGE, "OnDetour 訊息", "{body}")
        );
    }

    private Map<NotificationType, NotificationMessage> simplifiedChinese() {
        return Map.ofEntries(
                entry(NotificationType.EXPENSE_ADDED, "新增支出", "{actorName} 在“{tripName}”新增了“{expenseName}”（{amount} {currency}）"),
                entry(NotificationType.EXPENSE_UPDATED, "支出已更新", "{actorName} 更新了“{tripName}”中的“{expenseName}”"),
                entry(NotificationType.EXPENSE_DELETED, "支出已删除", "{actorName} 从“{tripName}”删除了“{expenseName}”"),
                entry(NotificationType.ACTIVITY_ADDED, "新增活动", "{actorName} 在“{tripName}”新增了“{activityName}”"),
                entry(NotificationType.ACTIVITY_UPDATED, "活动已更新", "{actorName} 更新了“{tripName}”中的“{activityName}”"),
                entry(NotificationType.ACTIVITY_DELETED, "活动已删除", "{actorName} 从“{tripName}”删除了“{activityName}”"),
                entry(NotificationType.MEMBER_ADDED, "新成员", "{actorName} 将 {newMemberName} 加入了“{tripName}”"),
                entry(NotificationType.MEMBER_REMOVED, "成员已移除", "{actorName} 将 {removedMemberName} 移出了“{tripName}”"),
                entry(NotificationType.MEMBER_ACCEPTED, "成员已加入", "{memberName} 已加入“{tripName}”"),
                entry(NotificationType.MEMBER_JOINED, "成员已加入", "{memberName} 已加入“{tripName}”"),
                entry(NotificationType.MEMBER_LEFT, "成员已退出", "{actorName} 已退出“{tripName}”"),
                entry(NotificationType.GUEST_LINKED, "访客已关联", "“{tripName}”中的 {guestName} 已关联到 {linkedUserName}"),
                entry(NotificationType.TRIP_UPDATED, "行程已更新", "{actorName} 更新了行程“{tripName}”"),
                entry(NotificationType.TRIP_DELETED, "行程已删除", "{actorName} 删除了行程“{tripName}”"),
                entry(NotificationType.PAYMENT_MARKED, "付款已更新", "{payerName} 更新了 {payeeName} 在“{expenseDescription}”中的付款（{amount} {currency}）"),
                entry(NotificationType.PAYMENT_REMINDER, "付款提醒", "{actorName} 提醒你支付“{expenseDescription}”的 {amount} {currency}。"),
                entry(NotificationType.SETTLEMENT_ADDED, "付款已记录", "{payerName} 已向 {payeeName} 支付 {amount} {currency}。"),
                entry(NotificationType.TODO_ASSIGNED, "你有一项新任务", "{actorName} 在“{tripName}”中把“{todoText}”分配给了你"),
                entry(NotificationType.PAYMENT_ALL_MARKED, "支出付款已更新", "{actorName} 将“{expenseDescription}”的付款标记为{paidStatus}"),
                entry(NotificationType.PAYMENT_TRIP_MARKED, "行程付款已更新", "{actorName} 将“{tripName}”中的付款标记为{paidStatus}"),
                entry(NotificationType.CHECKIN, "已打卡", "{actorName} 在“{tripName}”中于“{activityName}”打卡"),
                entry(NotificationType.NOTE_ADDED, "新增笔记", "{actorName} 为“{tripName}”中的“{activityName}”添加了笔记"),
                entry(NotificationType.NOTE_DELETED, "笔记已删除", "{actorName} 删除了“{tripName}”中“{activityName}”的笔记"),
                entry(NotificationType.COMMENT_ADDED, "新评论", "{actorName} 评论了“{tripName}”中的“{activityName}”"),
                entry(NotificationType.COMMENT_DELETED, "评论已删除", "{actorName} 删除了“{tripName}”中“{activityName}”的一条评论"),
                entry(NotificationType.TRIP_INVITE, "你收到了邀请", "你受邀加入“{tripName}”"),
                entry(NotificationType.TRIP_INVITE_DECLINED, "邀请已被拒绝", "{memberName} 拒绝了加入“{tripName}”的邀请"),
                entry(NotificationType.TRIP_INVITE_CANCELLED, "邀请已取消", "你加入“{tripName}”的邀请已被取消"),
                entry(NotificationType.MEMBER_INVITED, "已邀请成员", "{actorName} 邀请 {newMemberName} 加入“{tripName}”"),
                entry(NotificationType.MEMBER_JOIN_REQUESTED, "加入申请", "{memberName} 申请加入“{tripName}”"),
                entry(NotificationType.MEETUP_APPROVED, "活动已通过审核", "“{meetupTitle}”已显示在地图上"),
                entry(NotificationType.MEETUP_REJECTED, "活动未通过审核", "“{meetupTitle}”未通过审核。{reason}"),
                entry(NotificationType.MEETUP_REMOVED, "活动已移除", "“{meetupTitle}”已从地图上移除。{reason}"),
                entry(NotificationType.MEETUP_JOIN_REQUESTED, "新的加入申请", "{userName} 申请加入“{meetupTitle}”"),
                entry(NotificationType.MEETUP_PARTICIPANT_JOINED, "有人加入了", "{userName} 加入了“{meetupTitle}”"),
                entry(NotificationType.MEETUP_REQUEST_ACCEPTED, "申请已通过", "{hostName} 已同意你加入“{meetupTitle}”"),
                entry(NotificationType.MEETUP_REQUEST_DECLINED, "申请未通过", "{hostName} 无法接受你加入“{meetupTitle}”的申请"),
                entry(NotificationType.MEETUP_CANCELLED, "活动已取消", "{hostName} 发起的“{meetupTitle}”已取消。{reason}"),
                entry(NotificationType.MEETUP_INVITED, "你收到了邀请", "{hostName} 邀请你参加“{meetupTitle}”"),
                entry(NotificationType.LOCAL_EVENT_TOMORROW, "明天的活动", "“{eventTitle}”明天举行。点一下查看时间和地点"),
                entry(NotificationType.LOCAL_EVENT_STARTING_SOON, "即将开始", "“{eventTitle}”将于 {startTime} 开始"),
                entry(NotificationType.LOCAL_EVENT_OPENING_TODAY, "今天开幕", "“{eventTitle}”今天开始"),
                entry(NotificationType.LOCAL_EVENT_ENDING_SOON, "即将结束", "“{eventTitle}”将于 {endDate} 结束。你还没有打卡"),
                entry(NotificationType.LOCAL_EVENT_CANCELLED, "活动已取消", "“{eventTitle}”已取消"),
                entry(NotificationType.LOCAL_EVENT_POSTPONED, "活动延期", "“{eventTitle}”已延期，新日期未定"),
                entry(NotificationType.LOCAL_EVENT_RESCHEDULED, "活动改期", "“{eventTitle}”改到 {startDate}"),
                entry(NotificationType.MAP_LIMIT_REACHED, "地图搜索已达上限", "你今天的地图搜索次数已用完，将于 {resetTime} 恢复"),
                entry(NotificationType.MAP_SPAM_ALERT, "疑似地图滥用", "{userName} 今天有 {rejectedCount} 次地图请求被拒绝（{operation}）"),
                entry(NotificationType.MAP_BUDGET_WARNING, "地图预算提醒", "Mapbox {api} 已用掉本月上限的 {usedPercent}%"),
                entry(NotificationType.MAP_BUDGET_REACHED, "地图预算已达上限", "Mapbox {api} 已达上限，{resetTime} 前搜索将使用已保存的结果"),
                entry(NotificationType.PLACE_EDIT_APPROVED, "地点信息已更新", "谢谢！{placeName} 的信息已更新"),
                entry(NotificationType.PLACE_EDIT_REJECTED, "建议未被采用", "你对 {placeName} 的建议未被采用"),
                entry(NotificationType.MEMBER_ACCESS_GRANTED, "申请已通过", "你加入“{tripName}”的申请已通过"),
                entry(NotificationType.MEMBER_JOIN_REJECTED, "申请被拒绝", "你加入“{tripName}”的申请被拒绝了"),
                entry(NotificationType.MEMBER_ROLE_UPDATED, "成员角色已更新", "{actorName} 将 {memberName} 在“{tripName}”中的角色改为 {role}"),
                entry(NotificationType.GUEST_UPDATED, "访客信息已更新", "{actorName} 在“{tripName}”中将 {previousGuestName} 改名为 {guestName}"),
                entry(NotificationType.CHECKIN_UPDATED, "打卡已更新", "{actorName} 更新了“{tripName}”中“{activityName}”的打卡"),
                entry(NotificationType.NOTE_UPDATED, "笔记已更新", "{actorName} 更新了“{tripName}”中“{activityName}”的笔记"),
                entry(NotificationType.MEMORY_ADDED, "新的旅行回忆", "{actorName} 在“{tripName}”中添加了一段回忆"),
                entry(NotificationType.MEMORY_DELETED, "旅行回忆已删除", "{actorName} 从“{tripName}”删除了一段回忆"),
                entry(NotificationType.TRIP_BOOK_UPDATED, "旅行手册已更新", "{actorName} 更新了“{tripName}”的旅行手册"),
                entry(NotificationType.TRIP_CLONED, "行程被复制", "{actorName} 复制了你的行程“{tripName}”"),
                entry(NotificationType.ROUTE_OPTIMIZED, "路线已优化", "“{tripName}”的路线已完成优化"),
                entry(NotificationType.TRIP_REMINDER, "行程即将开始", "“{tripName}”即将开始，花点时间再确认一下行程吧"),
                entry(NotificationType.TRIP_STARTS_IN_ONE_WEEK, "还有一周出发", "“{tripName}”将在一周后开始，有空时确认一下行程和预订"),
                entry(NotificationType.TRIP_STARTS_IN_THREE_DAYS, "还有三天出发", "“{tripName}”将在三天后开始，请确认票务、交通和预订"),
                entry(NotificationType.TRIP_STARTS_IN_TWO_DAYS, "还有两天出发", "“{tripName}”将在两天后开始，先保存离线时可能用到的资料"),
                entry(NotificationType.TRIP_STARTS_IN_ONE_DAY, "明天出发", "“{tripName}”明天开始，最后确认一下行程和必备物品吧"),
                entry(NotificationType.TRIP_STARTS_IN_TWO_HOURS, "两小时后出发", "“{tripName}”将在两小时后开始，请确认第一站并预留足够的路上时间"),
                entry(NotificationType.TRIP_STARTED, "旅程开始了", "“{tripName}”已经开始，行程都已准备好，祝你旅途愉快！"),
                entry(NotificationType.ITINERARY_ITEM_PREPARATION, "准备好你的{itemKindLabel}", "“{itemName}”将在 {preparationLeadLabel}后开始。{preparationChecklist}"),
                entry(NotificationType.ITINERARY_ITEM_UPCOMING, "{itemKindLabel}还有 15 分钟开始", "下一项“{itemName}”将在 15 分钟后开始"),
                entry(NotificationType.ITINERARY_ITEM_COMPLETED, "“{itemName}”体验如何？", "你已完成“{itemName}”。写一条简短评价，留住这段经历吧。{nextItemSentence}"),
                entry(NotificationType.TRIP_DAY_ENDED, "第 {dayNumber} 天结束了", "“{tripName}”的第 {dayNumber} 天结束了。趁记忆犹新，去今天到过的地方打卡并写下简短评价吧"),
                entry(NotificationType.TRIP_DAY_WEATHER_ALERT, "明天天气预报：{weatherLabel}", "“{tripName}”的第 {dayNumber} 天可能受到影响。需要的话可以重新检查行程"),
                entry(NotificationType.TRIP_ENDED, "欢迎回来", "“{tripName}”已结束，希望你度过了一段美好的旅程！"),
                entry(NotificationType.TRIP_SUMMARY, "旅程回顾", "你在 {placesCount} 个地点共花费 {totalExpense} {currency}。{debtSummary}"),
                entry(NotificationType.MARKETPLACE_BOOKING_REQUEST, "新的预订请求", "有旅客提交了预订请求，请尽快查看"),
                entry(NotificationType.MARKETPLACE_BOOKING_CONFIRMED, "预订已确认", "你的预订已确认，一切准备就绪！"),
                entry(NotificationType.MARKETPLACE_BOOKING_DECLINED, "预订未确认", "你的预订请求未能确认，请打开应用查看其他选择"),
                entry(NotificationType.MARKETPLACE_BOOKING_EXPIRED, "预订请求已过期", "商家未能及时回复，你的请求已释放。你可以重新预订或选择其他选项"),
                entry(NotificationType.MARKETPLACE_BOOKING_CANCELLED_BY_GUEST, "旅客取消了预订", "旅客取消了 {bookingCode}，库存已释放"),
                entry(NotificationType.MARKETPLACE_BOOKING_UPDATED, "预订已更新", "{bookingCode} 当前状态为 {statusLabel}。打开“我的预订”查看详情"),
                entry(NotificationType.MARKETPLACE_CHANGE_REQUESTED, "变更请求", "旅客申请修改 {bookingCode}，请在请求过期前处理"),
                entry(NotificationType.MARKETPLACE_CHANGE_ANSWERED, "变更请求{decisionLabel}", "你对 {bookingCode} 的变更请求{decisionLabel}。打开预订查看详情"),
                entry(NotificationType.MARKETPLACE_REVIEW_INVITE, "这次住得怎么样？", "你的预订 {bookingCode} 已完成。写条评价，帮助其他旅行者吧"),
                entry(NotificationType.MARKETPLACE_MESSAGE, "新消息", "{senderName} 给你发了一条消息"),
                entry(NotificationType.CHAT_MENTION, "有人提到了你", "{senderName} 在 {conversationTitle} 中提到了你"),
                entry(NotificationType.PARTNER_VERIFICATION_DECIDED, "认证结果：{decisionLabel}", "你的机构 {organizationName} 认证结果：{decisionLabel}。{reason}"),
                entry(NotificationType.PARTNER_VERIFICATION_SUBMITTED, "合作伙伴已提交认证", "{organizationName} 已提交认证材料"),
                entry(NotificationType.PARTNER_STATEMENT_READY, "对账单已生成", "你 {periodLabel} 的对账单已生成：扣除佣金后为 {netAmount}。打开“财务”查看"),
                entry(NotificationType.ADMIN_ANNOUNCEMENT, "OnDetour 公告", "{body}"),
                entry(NotificationType.ADMIN_MESSAGE, "来自 OnDetour 的消息", "{body}")
        );
    }

    private Map<NotificationType, NotificationMessage> french() {
        return Map.ofEntries(
                entry(NotificationType.EXPENSE_ADDED, "Nouvelle dépense", "{actorName} a ajouté « {expenseName} » ({amount} {currency}) à « {tripName} »"),
                entry(NotificationType.EXPENSE_UPDATED, "Dépense modifiée", "{actorName} a modifié « {expenseName} » dans « {tripName} »"),
                entry(NotificationType.EXPENSE_DELETED, "Dépense supprimée", "{actorName} a supprimé « {expenseName} » de « {tripName} »"),
                entry(NotificationType.ACTIVITY_ADDED, "Nouvelle activité", "{actorName} a ajouté « {activityName} » à « {tripName} »"),
                entry(NotificationType.ACTIVITY_UPDATED, "Activité modifiée", "{actorName} a modifié « {activityName} » dans « {tripName} »"),
                entry(NotificationType.ACTIVITY_DELETED, "Activité supprimée", "{actorName} a supprimé « {activityName} » de « {tripName} »"),
                entry(NotificationType.MEMBER_ADDED, "Nouveau membre", "{actorName} a ajouté {newMemberName} à « {tripName} »"),
                entry(NotificationType.MEMBER_REMOVED, "Membre retiré", "{actorName} a retiré {removedMemberName} de « {tripName} »"),
                entry(NotificationType.MEMBER_ACCEPTED, "Nouveau participant", "{memberName} a rejoint « {tripName} »"),
                entry(NotificationType.MEMBER_JOINED, "Nouveau participant", "{memberName} a rejoint « {tripName} »"),
                entry(NotificationType.MEMBER_LEFT, "Départ d'un membre", "{actorName} a quitté « {tripName} »"),
                entry(NotificationType.GUEST_LINKED, "Profil invité associé", "{guestName} est maintenant associé à {linkedUserName} dans « {tripName} »"),
                entry(NotificationType.TRIP_UPDATED, "Voyage modifié", "{actorName} a modifié « {tripName} »"),
                entry(NotificationType.TRIP_DELETED, "Voyage supprimé", "{actorName} a supprimé « {tripName} »"),
                entry(NotificationType.PAYMENT_MARKED, "Paiement mis à jour", "{payerName} a mis à jour le paiement de {amount} {currency} de {payeeName} pour « {expenseDescription} »"),
                entry(NotificationType.PAYMENT_REMINDER, "Rappel de paiement", "{actorName} vous rappelle de régler {amount} {currency} pour « {expenseDescription} »."),
                entry(NotificationType.SETTLEMENT_ADDED, "Paiement enregistré", "{payerName} a payé {amount} {currency} à {payeeName}."),
                entry(NotificationType.TODO_ASSIGNED, "Tâche qui vous est attribuée", "{actorName} vous a attribué « {todoText} » dans « {tripName} »"),
                entry(NotificationType.PAYMENT_ALL_MARKED, "Paiements de la dépense mis à jour", "{actorName} a marqué les paiements de « {expenseDescription} » comme {paidStatus}"),
                entry(NotificationType.PAYMENT_TRIP_MARKED, "Paiements du voyage mis à jour", "{actorName} a marqué les paiements de « {tripName} » comme {paidStatus}"),
                entry(NotificationType.CHECKIN, "Check-in", "{actorName} a fait un check-in à « {activityName} » pendant « {tripName} »"),
                entry(NotificationType.NOTE_ADDED, "Note ajoutée", "{actorName} a ajouté une note à « {activityName} » dans « {tripName} »"),
                entry(NotificationType.NOTE_DELETED, "Note supprimée", "{actorName} a supprimé une note de « {activityName} » dans « {tripName} »"),
                entry(NotificationType.COMMENT_ADDED, "Nouveau commentaire", "{actorName} a commenté « {activityName} » dans « {tripName} »"),
                entry(NotificationType.COMMENT_DELETED, "Commentaire supprimé", "{actorName} a supprimé un commentaire de « {activityName} » dans « {tripName} »"),
                entry(NotificationType.TRIP_INVITE, "Vous êtes invité", "Vous avez été invité à rejoindre « {tripName} »"),
                entry(NotificationType.TRIP_INVITE_DECLINED, "Invitation refusée", "{memberName} a refusé l'invitation à « {tripName} »"),
                entry(NotificationType.TRIP_INVITE_CANCELLED, "Invitation annulée", "Votre invitation à « {tripName} » a été annulée"),
                entry(NotificationType.MEMBER_INVITED, "Membre invité", "{actorName} a invité {newMemberName} à « {tripName} »"),
                entry(NotificationType.MEMBER_JOIN_REQUESTED, "Demande pour rejoindre", "{memberName} demande à rejoindre « {tripName} »"),
                entry(NotificationType.MEETUP_APPROVED, "Rencontre approuvée", "« {meetupTitle} » est maintenant sur la carte"),
                entry(NotificationType.MEETUP_REJECTED, "Rencontre non approuvée", "« {meetupTitle} » n'a pas été approuvée. {reason}"),
                entry(NotificationType.MEETUP_REMOVED, "Rencontre retirée", "« {meetupTitle} » a été retirée de la carte. {reason}"),
                entry(NotificationType.MEETUP_JOIN_REQUESTED, "Nouvelle demande", "{userName} demande à rejoindre « {meetupTitle} »"),
                entry(NotificationType.MEETUP_PARTICIPANT_JOINED, "Un nouveau participant", "{userName} a rejoint « {meetupTitle} »"),
                entry(NotificationType.MEETUP_REQUEST_ACCEPTED, "Demande acceptée", "{hostName} a accepté votre demande pour rejoindre « {meetupTitle} »"),
                entry(NotificationType.MEETUP_REQUEST_DECLINED, "Demande non acceptée", "{hostName} n'a pas pu accepter votre demande pour rejoindre « {meetupTitle} »"),
                entry(NotificationType.MEETUP_CANCELLED, "Rencontre annulée", "« {meetupTitle} » de {hostName} a été annulée. {reason}"),
                entry(NotificationType.MEETUP_INVITED, "Vous êtes invité", "{hostName} vous invite à « {meetupTitle} »"),
                entry(NotificationType.LOCAL_EVENT_TOMORROW, "Événement demain", "« {eventTitle} » a lieu demain. Touchez pour voir l’heure et le lieu"),
                entry(NotificationType.LOCAL_EVENT_STARTING_SOON, "Ça commence bientôt", "« {eventTitle} » commence à {startTime}"),
                entry(NotificationType.LOCAL_EVENT_OPENING_TODAY, "Ouverture aujourd’hui", "« {eventTitle} » ouvre aujourd’hui"),
                entry(NotificationType.LOCAL_EVENT_ENDING_SOON, "Bientôt terminé", "« {eventTitle} » se termine le {endDate}. Vous n’avez pas encore fait de check-in"),
                entry(NotificationType.LOCAL_EVENT_CANCELLED, "Événement annulé", "« {eventTitle} » a été annulé"),
                entry(NotificationType.LOCAL_EVENT_POSTPONED, "Événement reporté", "« {eventTitle} » a été reporté. Aucune nouvelle date pour l’instant"),
                entry(NotificationType.LOCAL_EVENT_RESCHEDULED, "Nouvelle date", "« {eventTitle} » est déplacé au {startDate}"),
                entry(NotificationType.MAP_LIMIT_REACHED, "Limite de recherche atteinte", "Vous avez utilisé vos recherches sur la carte du jour. Elles reviennent à {resetTime}"),
                entry(NotificationType.MAP_SPAM_ALERT, "Abus possible de la carte", "{userName} s'est vu refuser {rejectedCount} requêtes de carte aujourd'hui ({operation})"),
                entry(NotificationType.MAP_BUDGET_WARNING, "Alerte budget carte", "Mapbox {api} a utilisé {usedPercent} % du plafond de ce mois"),
                entry(NotificationType.MAP_BUDGET_REACHED, "Budget carte atteint", "Mapbox {api} a atteint son plafond ; les recherches utilisent les résultats enregistrés jusqu'à {resetTime}"),
                entry(NotificationType.PLACE_EDIT_APPROVED, "Lieu mis à jour", "Merci ! Les informations sur {placeName} ont été mises à jour"),
                entry(NotificationType.PLACE_EDIT_REJECTED, "Suggestion non appliquée", "Votre suggestion pour {placeName} n'a pas été appliquée"),
                entry(NotificationType.MEMBER_ACCESS_GRANTED, "Demande acceptée", "Votre demande pour rejoindre « {tripName} » a été acceptée"),
                entry(NotificationType.MEMBER_JOIN_REJECTED, "Demande refusée", "Votre demande pour rejoindre « {tripName} » a été refusée"),
                entry(NotificationType.MEMBER_ROLE_UPDATED, "Rôle modifié", "{actorName} a changé le rôle de {memberName} dans « {tripName} » en {role}"),
                entry(NotificationType.GUEST_UPDATED, "Invité modifié", "{actorName} a renommé {previousGuestName} en {guestName} dans « {tripName} »"),
                entry(NotificationType.CHECKIN_UPDATED, "Check-in modifié", "{actorName} a modifié un check-in à « {activityName} » dans « {tripName} »"),
                entry(NotificationType.NOTE_UPDATED, "Note modifiée", "{actorName} a modifié une note de « {activityName} » dans « {tripName} »"),
                entry(NotificationType.MEMORY_ADDED, "Nouveau souvenir", "{actorName} a ajouté un souvenir à « {tripName} »"),
                entry(NotificationType.MEMORY_DELETED, "Souvenir supprimé", "{actorName} a supprimé un souvenir de « {tripName} »"),
                entry(NotificationType.TRIP_BOOK_UPDATED, "Carnet de voyage modifié", "{actorName} a modifié le carnet de voyage de « {tripName} »"),
                entry(NotificationType.TRIP_CLONED, "Voyage copié", "{actorName} a copié votre voyage « {tripName} »"),
                entry(NotificationType.ROUTE_OPTIMIZED, "Itinéraire prêt", "L'itinéraire de « {tripName} » a été optimisé"),
                entry(NotificationType.TRIP_REMINDER, "Voyage imminent", "« {tripName} » approche. Prenez un moment pour revoir votre programme"),
                entry(NotificationType.TRIP_STARTS_IN_ONE_WEEK, "Plus qu'une semaine", "« {tripName} » commence dans une semaine. Revoyez votre programme et vos réservations quand vous avez un moment"),
                entry(NotificationType.TRIP_STARTS_IN_THREE_DAYS, "Plus que trois jours", "« {tripName} » commence dans trois jours. Vérifiez vos billets, vos transports et vos réservations"),
                entry(NotificationType.TRIP_STARTS_IN_TWO_DAYS, "Plus que deux jours", "« {tripName} » commence dans deux jours. Enregistrez ce dont vous pourriez avoir besoin hors ligne"),
                entry(NotificationType.TRIP_STARTS_IN_ONE_DAY, "Départ demain", "« {tripName} » commence demain. Jetez un dernier coup d'œil à votre programme et à l'essentiel"),
                entry(NotificationType.TRIP_STARTS_IN_TWO_HOURS, "Départ dans deux heures", "« {tripName} » commence dans deux heures. Vérifiez votre première étape et prévoyez assez de temps de trajet"),
                entry(NotificationType.TRIP_STARTED, "C'est parti", "« {tripName} » a commencé. Votre programme est prêt, bon voyage !"),
                entry(NotificationType.ITINERARY_ITEM_PREPARATION, "Préparez votre {itemKindLabel}", "« {itemName} » commence dans {preparationLeadLabel}. {preparationChecklist}"),
                entry(NotificationType.ITINERARY_ITEM_UPCOMING, "{itemKindLabel} dans 15 minutes", "« {itemName} » est la prochaine étape et commence dans 15 minutes"),
                entry(NotificationType.ITINERARY_ITEM_COMPLETED, "Comment était « {itemName} » ?", "Vous avez terminé « {itemName} ». Laissez un petit avis pour garder ce souvenir. {nextItemSentence}"),
                entry(NotificationType.TRIP_DAY_ENDED, "Fin du jour {dayNumber}", "Le jour {dayNumber} de « {tripName} » est terminé. Faites un check-in aux lieux visités et laissez un petit avis tant que c'est frais"),
                entry(NotificationType.TRIP_DAY_WEATHER_ALERT, "Météo de demain : {weatherLabel}", "Le jour {dayNumber} de « {tripName} » pourrait être perturbé. Revoyez le programme si besoin"),
                entry(NotificationType.TRIP_ENDED, "Bon retour", "« {tripName} » est terminé. Nous espérons que vous avez fait un merveilleux voyage !"),
                entry(NotificationType.TRIP_SUMMARY, "Votre voyage en bref", "Vous avez dépensé {totalExpense} {currency} dans {placesCount} lieux. {debtSummary}"),
                entry(NotificationType.MARKETPLACE_BOOKING_REQUEST, "Nouvelle demande de réservation", "Un voyageur a envoyé une demande de réservation. Consultez-la dès que possible"),
                entry(NotificationType.MARKETPLACE_BOOKING_CONFIRMED, "Réservation confirmée", "Votre réservation est confirmée. Tout est prêt !"),
                entry(NotificationType.MARKETPLACE_BOOKING_DECLINED, "Réservation non confirmée", "Votre demande de réservation n'a pas pu être confirmée. Ouvrez l'application pour voir les autres options"),
                entry(NotificationType.MARKETPLACE_BOOKING_EXPIRED, "Demande de réservation expirée", "Le partenaire n'a pas répondu à temps, votre demande a donc été libérée. Vous pouvez réserver à nouveau ou choisir une autre option"),
                entry(NotificationType.MARKETPLACE_BOOKING_CANCELLED_BY_GUEST, "Réservation annulée par un voyageur", "Un voyageur a annulé {bookingCode}. Les disponibilités ont été libérées"),
                entry(NotificationType.MARKETPLACE_BOOKING_UPDATED, "Réservation mise à jour", "{bookingCode} est maintenant {statusLabel}. Ouvrez Mes réservations pour les détails"),
                entry(NotificationType.MARKETPLACE_CHANGE_REQUESTED, "Demande de modification", "Un voyageur souhaite modifier {bookingCode}. Traitez la demande avant son expiration"),
                entry(NotificationType.MARKETPLACE_CHANGE_ANSWERED, "Demande de modification : {decisionLabel}", "Votre demande de modification pour {bookingCode} : {decisionLabel}. Ouvrez la réservation pour les détails"),
                entry(NotificationType.MARKETPLACE_REVIEW_INVITE, "Comment s'est passé votre séjour ?", "Votre réservation {bookingCode} est terminée. Laissez un avis pour aider les autres voyageurs"),
                entry(NotificationType.MARKETPLACE_MESSAGE, "Nouveau message", "{senderName} vous a envoyé un message"),
                entry(NotificationType.CHAT_MENTION, "Vous avez été mentionné", "{senderName} vous a mentionné dans {conversationTitle}"),
                entry(NotificationType.PARTNER_VERIFICATION_DECIDED, "Vérification : {decisionLabel}", "Votre organisation {organizationName} : {decisionLabel}. {reason}"),
                entry(NotificationType.PARTNER_VERIFICATION_SUBMITTED, "Vérification partenaire envoyée", "{organizationName} a envoyé des documents pour vérification"),
                entry(NotificationType.PARTNER_STATEMENT_READY, "Relevé disponible", "Votre relevé pour {periodLabel} est prêt : {netAmount} après commission. Ouvrez Finances pour le consulter"),
                entry(NotificationType.ADMIN_ANNOUNCEMENT, "Annonce OnDetour", "{body}"),
                entry(NotificationType.ADMIN_MESSAGE, "Message d'OnDetour", "{body}")
        );
    }

    private Map<NotificationType, NotificationMessage> german() {
        return Map.ofEntries(
                entry(NotificationType.EXPENSE_ADDED, "Neue Ausgabe", "{actorName} hat „{expenseName}“ ({amount} {currency}) zu „{tripName}“ hinzugefügt"),
                entry(NotificationType.EXPENSE_UPDATED, "Ausgabe geändert", "{actorName} hat „{expenseName}“ in „{tripName}“ geändert"),
                entry(NotificationType.EXPENSE_DELETED, "Ausgabe entfernt", "{actorName} hat „{expenseName}“ aus „{tripName}“ entfernt"),
                entry(NotificationType.ACTIVITY_ADDED, "Neue Aktivität", "{actorName} hat „{activityName}“ zu „{tripName}“ hinzugefügt"),
                entry(NotificationType.ACTIVITY_UPDATED, "Aktivität geändert", "{actorName} hat „{activityName}“ in „{tripName}“ geändert"),
                entry(NotificationType.ACTIVITY_DELETED, "Aktivität entfernt", "{actorName} hat „{activityName}“ aus „{tripName}“ entfernt"),
                entry(NotificationType.MEMBER_ADDED, "Neues Mitglied", "{actorName} hat {newMemberName} zu „{tripName}“ hinzugefügt"),
                entry(NotificationType.MEMBER_REMOVED, "Mitglied entfernt", "{actorName} hat {removedMemberName} aus „{tripName}“ entfernt"),
                entry(NotificationType.MEMBER_ACCEPTED, "Mitglied beigetreten", "{memberName} ist „{tripName}“ beigetreten"),
                entry(NotificationType.MEMBER_JOINED, "Mitglied beigetreten", "{memberName} ist „{tripName}“ beigetreten"),
                entry(NotificationType.MEMBER_LEFT, "Mitglied ausgetreten", "{actorName} hat „{tripName}“ verlassen"),
                entry(NotificationType.GUEST_LINKED, "Gastprofil verknüpft", "{guestName} ist jetzt in „{tripName}“ mit {linkedUserName} verknüpft"),
                entry(NotificationType.TRIP_UPDATED, "Reise geändert", "{actorName} hat „{tripName}“ geändert"),
                entry(NotificationType.TRIP_DELETED, "Reise gelöscht", "{actorName} hat „{tripName}“ gelöscht"),
                entry(NotificationType.PAYMENT_MARKED, "Zahlung aktualisiert", "{payerName} hat die Zahlung von {payeeName} über {amount} {currency} für „{expenseDescription}“ aktualisiert"),
                entry(NotificationType.PAYMENT_REMINDER, "Zahlungserinnerung", "{actorName} erinnert dich an {amount} {currency} für „{expenseDescription}“."),
                entry(NotificationType.SETTLEMENT_ADDED, "Zahlung erfasst", "{payerName} hat {payeeName} {amount} {currency} gezahlt."),
                entry(NotificationType.TODO_ASSIGNED, "Aufgabe für dich", "{actorName} hat dir „{todoText}“ in „{tripName}“ zugewiesen"),
                entry(NotificationType.PAYMENT_ALL_MARKED, "Zahlungen der Ausgabe aktualisiert", "{actorName} hat die Zahlungen für „{expenseDescription}“ als {paidStatus} markiert"),
                entry(NotificationType.PAYMENT_TRIP_MARKED, "Zahlungen der Reise aktualisiert", "{actorName} hat die Zahlungen in „{tripName}“ als {paidStatus} markiert"),
                entry(NotificationType.CHECKIN, "Eingecheckt", "{actorName} hat während „{tripName}“ bei „{activityName}“ eingecheckt"),
                entry(NotificationType.NOTE_ADDED, "Notiz hinzugefügt", "{actorName} hat eine Notiz zu „{activityName}“ in „{tripName}“ hinzugefügt"),
                entry(NotificationType.NOTE_DELETED, "Notiz entfernt", "{actorName} hat eine Notiz von „{activityName}“ in „{tripName}“ entfernt"),
                entry(NotificationType.COMMENT_ADDED, "Neuer Kommentar", "{actorName} hat „{activityName}“ in „{tripName}“ kommentiert"),
                entry(NotificationType.COMMENT_DELETED, "Kommentar entfernt", "{actorName} hat einen Kommentar von „{activityName}“ in „{tripName}“ entfernt"),
                entry(NotificationType.TRIP_INVITE, "Du bist eingeladen", "Du wurdest eingeladen, „{tripName}“ beizutreten"),
                entry(NotificationType.TRIP_INVITE_DECLINED, "Einladung abgelehnt", "{memberName} hat die Einladung zu „{tripName}“ abgelehnt"),
                entry(NotificationType.TRIP_INVITE_CANCELLED, "Einladung zurückgezogen", "Deine Einladung zu „{tripName}“ wurde zurückgezogen"),
                entry(NotificationType.MEMBER_INVITED, "Mitglied eingeladen", "{actorName} hat {newMemberName} zu „{tripName}“ eingeladen"),
                entry(NotificationType.MEMBER_JOIN_REQUESTED, "Beitrittsanfrage", "{memberName} möchte „{tripName}“ beitreten"),
                entry(NotificationType.MEETUP_APPROVED, "Treffen freigegeben", "„{meetupTitle}“ ist jetzt auf der Karte"),
                entry(NotificationType.MEETUP_REJECTED, "Treffen nicht freigegeben", "„{meetupTitle}“ wurde nicht freigegeben. {reason}"),
                entry(NotificationType.MEETUP_REMOVED, "Treffen entfernt", "„{meetupTitle}“ wurde von der Karte entfernt. {reason}"),
                entry(NotificationType.MEETUP_JOIN_REQUESTED, "Neue Beitrittsanfrage", "{userName} möchte bei „{meetupTitle}“ mitmachen"),
                entry(NotificationType.MEETUP_PARTICIPANT_JOINED, "Jemand ist dabei", "{userName} ist bei „{meetupTitle}“ dabei"),
                entry(NotificationType.MEETUP_REQUEST_ACCEPTED, "Du bist dabei", "{hostName} hat deine Anfrage für „{meetupTitle}“ angenommen"),
                entry(NotificationType.MEETUP_REQUEST_DECLINED, "Anfrage nicht angenommen", "{hostName} konnte deine Anfrage für „{meetupTitle}“ nicht annehmen"),
                entry(NotificationType.MEETUP_CANCELLED, "Treffen abgesagt", "„{meetupTitle}“ von {hostName} wurde abgesagt. {reason}"),
                entry(NotificationType.MEETUP_INVITED, "Du bist eingeladen", "{hostName} hat dich zu „{meetupTitle}“ eingeladen"),
                entry(NotificationType.LOCAL_EVENT_TOMORROW, "Event morgen", "„{eventTitle}“ findet morgen statt. Tippe, um Zeit und Ort zu sehen"),
                entry(NotificationType.LOCAL_EVENT_STARTING_SOON, "Beginnt bald", "„{eventTitle}“ beginnt um {startTime}"),
                entry(NotificationType.LOCAL_EVENT_OPENING_TODAY, "Eröffnung heute", "„{eventTitle}“ öffnet heute"),
                entry(NotificationType.LOCAL_EVENT_ENDING_SOON, "Endet bald", "„{eventTitle}“ endet am {endDate}. Du hast noch nicht eingecheckt"),
                entry(NotificationType.LOCAL_EVENT_CANCELLED, "Event abgesagt", "„{eventTitle}“ wurde abgesagt"),
                entry(NotificationType.LOCAL_EVENT_POSTPONED, "Event verschoben", "„{eventTitle}“ wurde verschoben. Es gibt noch keinen neuen Termin"),
                entry(NotificationType.LOCAL_EVENT_RESCHEDULED, "Neuer Termin", "„{eventTitle}“ findet jetzt am {startDate} statt"),
                entry(NotificationType.MAP_LIMIT_REACHED, "Kartensuche ausgeschöpft", "Du hast die Kartensuchen für heute aufgebraucht. Sie stehen ab {resetTime} wieder zur Verfügung"),
                entry(NotificationType.MAP_SPAM_ALERT, "Möglicher Kartenmissbrauch", "{userName} wurden heute {rejectedCount} Kartenanfragen verweigert ({operation})"),
                entry(NotificationType.MAP_BUDGET_WARNING, "Kartenbudget-Warnung", "Mapbox {api} hat {usedPercent} % des Monatslimits verbraucht"),
                entry(NotificationType.MAP_BUDGET_REACHED, "Kartenbudget erreicht", "Mapbox {api} hat das Limit erreicht; Suchen nutzen bis {resetTime} gespeicherte Ergebnisse"),
                entry(NotificationType.PLACE_EDIT_APPROVED, "Ort aktualisiert", "Danke! Die Angaben zu {placeName} wurden aktualisiert"),
                entry(NotificationType.PLACE_EDIT_REJECTED, "Vorschlag nicht übernommen", "Dein Vorschlag für {placeName} wurde nicht übernommen"),
                entry(NotificationType.MEMBER_ACCESS_GRANTED, "Anfrage angenommen", "Deine Anfrage, „{tripName}“ beizutreten, wurde angenommen"),
                entry(NotificationType.MEMBER_JOIN_REJECTED, "Anfrage abgelehnt", "Deine Anfrage, „{tripName}“ beizutreten, wurde abgelehnt"),
                entry(NotificationType.MEMBER_ROLE_UPDATED, "Rolle geändert", "{actorName} hat die Rolle von {memberName} in „{tripName}“ auf {role} geändert"),
                entry(NotificationType.GUEST_UPDATED, "Gast geändert", "{actorName} hat {previousGuestName} in „{tripName}“ in {guestName} umbenannt"),
                entry(NotificationType.CHECKIN_UPDATED, "Check-in geändert", "{actorName} hat einen Check-in bei „{activityName}“ in „{tripName}“ geändert"),
                entry(NotificationType.NOTE_UPDATED, "Notiz geändert", "{actorName} hat eine Notiz zu „{activityName}“ in „{tripName}“ geändert"),
                entry(NotificationType.MEMORY_ADDED, "Neue Reiseerinnerung", "{actorName} hat „{tripName}“ eine Erinnerung hinzugefügt"),
                entry(NotificationType.MEMORY_DELETED, "Reiseerinnerung entfernt", "{actorName} hat eine Erinnerung aus „{tripName}“ entfernt"),
                entry(NotificationType.TRIP_BOOK_UPDATED, "Reisebuch aktualisiert", "{actorName} hat das Reisebuch von „{tripName}“ aktualisiert"),
                entry(NotificationType.TRIP_CLONED, "Reise kopiert", "{actorName} hat deine Reise „{tripName}“ kopiert"),
                entry(NotificationType.ROUTE_OPTIMIZED, "Route bereit", "Die Route für „{tripName}“ wurde optimiert"),
                entry(NotificationType.TRIP_REMINDER, "Reise steht bevor", "„{tripName}“ steht bevor. Nimm dir kurz Zeit, deinen Reiseplan durchzugehen"),
                entry(NotificationType.TRIP_STARTS_IN_ONE_WEEK, "Noch eine Woche", "„{tripName}“ beginnt in einer Woche. Prüfe bei Gelegenheit deinen Reiseplan und deine Buchungen"),
                entry(NotificationType.TRIP_STARTS_IN_THREE_DAYS, "Noch drei Tage", "„{tripName}“ beginnt in drei Tagen. Prüfe Tickets, Verkehrsmittel und Buchungen"),
                entry(NotificationType.TRIP_STARTS_IN_TWO_DAYS, "Noch zwei Tage", "„{tripName}“ beginnt in zwei Tagen. Speichere alles, was du offline brauchen könntest"),
                entry(NotificationType.TRIP_STARTS_IN_ONE_DAY, "Morgen geht es los", "„{tripName}“ beginnt morgen. Wirf einen letzten Blick auf Reiseplan und das Wichtigste"),
                entry(NotificationType.TRIP_STARTS_IN_TWO_HOURS, "Abfahrt in zwei Stunden", "„{tripName}“ beginnt in zwei Stunden. Prüfe deinen ersten Halt und plane genug Fahrzeit ein"),
                entry(NotificationType.TRIP_STARTED, "Deine Reise beginnt", "„{tripName}“ hat begonnen. Dein Reiseplan ist bereit – gute Reise!"),
                entry(NotificationType.ITINERARY_ITEM_PREPARATION, "Bereit machen: {itemKindLabel}", "„{itemName}“ beginnt in {preparationLeadLabel}. {preparationChecklist}"),
                entry(NotificationType.ITINERARY_ITEM_UPCOMING, "{itemKindLabel} in 15 Minuten", "„{itemName}“ ist als Nächstes dran und beginnt in 15 Minuten"),
                entry(NotificationType.ITINERARY_ITEM_COMPLETED, "Wie war „{itemName}“?", "Du hast „{itemName}“ abgeschlossen. Schreib eine kurze Bewertung, um dich daran zu erinnern. {nextItemSentence}"),
                entry(NotificationType.TRIP_DAY_ENDED, "Tag {dayNumber} ist geschafft", "Tag {dayNumber} von „{tripName}“ ist vorbei. Checke an den besuchten Orten ein und schreib eine kurze Bewertung, solange die Eindrücke frisch sind"),
                entry(NotificationType.TRIP_DAY_WEATHER_ALERT, "Wetter für morgen: {weatherLabel}", "Tag {dayNumber} von „{tripName}“ könnte betroffen sein. Prüfe den Plan, falls du ihn ändern möchtest"),
                entry(NotificationType.TRIP_ENDED, "Willkommen zurück", "„{tripName}“ ist zu Ende. Wir hoffen, du hattest eine wunderbare Reise!"),
                entry(NotificationType.TRIP_SUMMARY, "Deine Reise auf einen Blick", "Du hast {totalExpense} {currency} an {placesCount} Orten ausgegeben. {debtSummary}"),
                entry(NotificationType.MARKETPLACE_BOOKING_REQUEST, "Neue Buchungsanfrage", "Ein Gast hat eine Buchungsanfrage gesendet. Bitte prüfe sie, sobald du kannst"),
                entry(NotificationType.MARKETPLACE_BOOKING_CONFIRMED, "Buchung bestätigt", "Deine Buchung ist bestätigt. Alles erledigt!"),
                entry(NotificationType.MARKETPLACE_BOOKING_DECLINED, "Buchung nicht bestätigt", "Deine Buchungsanfrage konnte nicht bestätigt werden. Öffne die App, um andere Optionen zu sehen"),
                entry(NotificationType.MARKETPLACE_BOOKING_EXPIRED, "Buchungsanfrage abgelaufen", "Der Partner hat nicht rechtzeitig geantwortet, deshalb wurde deine Anfrage freigegeben. Du kannst erneut buchen oder eine andere Option wählen"),
                entry(NotificationType.MARKETPLACE_BOOKING_CANCELLED_BY_GUEST, "Gast hat storniert", "Ein Gast hat {bookingCode} storniert. Das Kontingent wurde freigegeben"),
                entry(NotificationType.MARKETPLACE_BOOKING_UPDATED, "Buchung aktualisiert", "{bookingCode} ist jetzt {statusLabel}. Details findest du unter Meine Buchungen"),
                entry(NotificationType.MARKETPLACE_CHANGE_REQUESTED, "Änderungsanfrage", "Ein Gast möchte {bookingCode} ändern. Prüfe die Anfrage, bevor sie abläuft"),
                entry(NotificationType.MARKETPLACE_CHANGE_ANSWERED, "Änderungsanfrage: {decisionLabel}", "Deine Änderungsanfrage für {bookingCode}: {decisionLabel}. Öffne die Buchung für Details"),
                entry(NotificationType.MARKETPLACE_REVIEW_INVITE, "Wie war dein Aufenthalt?", "Deine Buchung {bookingCode} ist abgeschlossen. Schreib eine Bewertung, um anderen Reisenden zu helfen"),
                entry(NotificationType.MARKETPLACE_MESSAGE, "Neue Nachricht", "{senderName} hat dir eine Nachricht gesendet"),
                entry(NotificationType.CHAT_MENTION, "Du wurdest erwähnt", "{senderName} hat dich in {conversationTitle} erwähnt"),
                entry(NotificationType.PARTNER_VERIFICATION_DECIDED, "Verifizierung: {decisionLabel}", "Deine Organisation {organizationName}: {decisionLabel}. {reason}"),
                entry(NotificationType.PARTNER_VERIFICATION_SUBMITTED, "Partnerverifizierung eingereicht", "{organizationName} hat Unterlagen zur Verifizierung eingereicht"),
                entry(NotificationType.PARTNER_STATEMENT_READY, "Abrechnung bereit", "Deine Abrechnung für {periodLabel} ist fertig: {netAmount} nach Provision. Öffne Finanzen, um sie anzusehen"),
                entry(NotificationType.ADMIN_ANNOUNCEMENT, "OnDetour-Mitteilung", "{body}"),
                entry(NotificationType.ADMIN_MESSAGE, "Nachricht von OnDetour", "{body}")
        );
    }

    private Map<NotificationType, NotificationMessage> spanish() {
        return Map.ofEntries(
                entry(NotificationType.EXPENSE_ADDED, "Nuevo gasto", "{actorName} añadió “{expenseName}” ({amount} {currency}) a “{tripName}”"),
                entry(NotificationType.EXPENSE_UPDATED, "Gasto actualizado", "{actorName} actualizó “{expenseName}” en “{tripName}”"),
                entry(NotificationType.EXPENSE_DELETED, "Gasto eliminado", "{actorName} eliminó “{expenseName}” de “{tripName}”"),
                entry(NotificationType.ACTIVITY_ADDED, "Nueva actividad", "{actorName} añadió “{activityName}” a “{tripName}”"),
                entry(NotificationType.ACTIVITY_UPDATED, "Actividad actualizada", "{actorName} actualizó “{activityName}” en “{tripName}”"),
                entry(NotificationType.ACTIVITY_DELETED, "Actividad eliminada", "{actorName} eliminó “{activityName}” de “{tripName}”"),
                entry(NotificationType.MEMBER_ADDED, "Nuevo miembro", "{actorName} añadió a {newMemberName} a “{tripName}”"),
                entry(NotificationType.MEMBER_REMOVED, "Miembro eliminado", "{actorName} quitó a {removedMemberName} de “{tripName}”"),
                entry(NotificationType.MEMBER_ACCEPTED, "Nuevo participante", "{memberName} se unió a “{tripName}”"),
                entry(NotificationType.MEMBER_JOINED, "Nuevo participante", "{memberName} se unió a “{tripName}”"),
                entry(NotificationType.MEMBER_LEFT, "Un miembro salió", "{actorName} salió de “{tripName}”"),
                entry(NotificationType.GUEST_LINKED, "Perfil de invitado vinculado", "{guestName} ahora está vinculado a {linkedUserName} en “{tripName}”"),
                entry(NotificationType.TRIP_UPDATED, "Viaje actualizado", "{actorName} actualizó “{tripName}”"),
                entry(NotificationType.TRIP_DELETED, "Viaje eliminado", "{actorName} eliminó “{tripName}”"),
                entry(NotificationType.PAYMENT_MARKED, "Pago actualizado", "{payerName} actualizó el pago de {amount} {currency} de {payeeName} por “{expenseDescription}”"),
                entry(NotificationType.PAYMENT_REMINDER, "Recordatorio de pago", "{actorName} te recordó pagar {amount} {currency} por “{expenseDescription}”."),
                entry(NotificationType.SETTLEMENT_ADDED, "Pago registrado", "{payerName} pagó {amount} {currency} a {payeeName}."),
                entry(NotificationType.TODO_ASSIGNED, "Tarea asignada", "{actorName} te asignó “{todoText}” en “{tripName}”"),
                entry(NotificationType.PAYMENT_ALL_MARKED, "Pagos del gasto actualizados", "{actorName} marcó los pagos de “{expenseDescription}” como {paidStatus}"),
                entry(NotificationType.PAYMENT_TRIP_MARKED, "Pagos del viaje actualizados", "{actorName} marcó los pagos de “{tripName}” como {paidStatus}"),
                entry(NotificationType.CHECKIN, "Check-in realizado", "{actorName} hizo check-in en “{activityName}” durante “{tripName}”"),
                entry(NotificationType.NOTE_ADDED, "Nota añadida", "{actorName} añadió una nota a “{activityName}” en “{tripName}”"),
                entry(NotificationType.NOTE_DELETED, "Nota eliminada", "{actorName} eliminó una nota de “{activityName}” en “{tripName}”"),
                entry(NotificationType.COMMENT_ADDED, "Nuevo comentario", "{actorName} comentó “{activityName}” en “{tripName}”"),
                entry(NotificationType.COMMENT_DELETED, "Comentario eliminado", "{actorName} eliminó un comentario de “{activityName}” en “{tripName}”"),
                entry(NotificationType.TRIP_INVITE, "Tienes una invitación", "Te invitaron a unirte a “{tripName}”"),
                entry(NotificationType.TRIP_INVITE_DECLINED, "Invitación rechazada", "{memberName} rechazó la invitación a “{tripName}”"),
                entry(NotificationType.TRIP_INVITE_CANCELLED, "Invitación cancelada", "Se canceló tu invitación a “{tripName}”"),
                entry(NotificationType.MEMBER_INVITED, "Miembro invitado", "{actorName} invitó a {newMemberName} a “{tripName}”"),
                entry(NotificationType.MEMBER_JOIN_REQUESTED, "Solicitud para unirse", "{memberName} pidió unirse a “{tripName}”"),
                entry(NotificationType.MEETUP_APPROVED, "Quedada aprobada", "“{meetupTitle}” ya está en el mapa"),
                entry(NotificationType.MEETUP_REJECTED, "Quedada no aprobada", "“{meetupTitle}” no fue aprobada. {reason}"),
                entry(NotificationType.MEETUP_REMOVED, "Quedada retirada", "“{meetupTitle}” se retiró del mapa. {reason}"),
                entry(NotificationType.MEETUP_JOIN_REQUESTED, "Nueva solicitud", "{userName} pidió unirse a “{meetupTitle}”"),
                entry(NotificationType.MEETUP_PARTICIPANT_JOINED, "Alguien se unió", "{userName} se unió a “{meetupTitle}”"),
                entry(NotificationType.MEETUP_REQUEST_ACCEPTED, "Ya estás dentro", "{hostName} aceptó tu solicitud para unirte a “{meetupTitle}”"),
                entry(NotificationType.MEETUP_REQUEST_DECLINED, "Solicitud no aceptada", "{hostName} no pudo aceptar tu solicitud para unirte a “{meetupTitle}”"),
                entry(NotificationType.MEETUP_CANCELLED, "Quedada cancelada", "Se canceló “{meetupTitle}” de {hostName}. {reason}"),
                entry(NotificationType.MEETUP_INVITED, "Tienes una invitación", "{hostName} te invitó a “{meetupTitle}”"),
                entry(NotificationType.LOCAL_EVENT_TOMORROW, "Evento mañana", "“{eventTitle}” es mañana. Toca para ver la hora y el lugar"),
                entry(NotificationType.LOCAL_EVENT_STARTING_SOON, "Empieza pronto", "“{eventTitle}” empieza a las {startTime}"),
                entry(NotificationType.LOCAL_EVENT_OPENING_TODAY, "Abre hoy", "“{eventTitle}” abre hoy"),
                entry(NotificationType.LOCAL_EVENT_ENDING_SOON, "Termina pronto", "“{eventTitle}” termina el {endDate}. Aún no has hecho check-in"),
                entry(NotificationType.LOCAL_EVENT_CANCELLED, "Evento cancelado", "Se canceló “{eventTitle}”"),
                entry(NotificationType.LOCAL_EVENT_POSTPONED, "Evento pospuesto", "Se pospuso “{eventTitle}”. Aún no hay nueva fecha"),
                entry(NotificationType.LOCAL_EVENT_RESCHEDULED, "Evento reprogramado", "“{eventTitle}” pasa al {startDate}"),
                entry(NotificationType.MAP_LIMIT_REACHED, "Límite de búsquedas alcanzado", "Ya usaste las búsquedas en el mapa de hoy. Vuelven a estar disponibles a las {resetTime}"),
                entry(NotificationType.MAP_SPAM_ALERT, "Posible abuso del mapa", "A {userName} se le rechazaron {rejectedCount} solicitudes de mapa hoy ({operation})"),
                entry(NotificationType.MAP_BUDGET_WARNING, "Aviso de presupuesto del mapa", "Mapbox {api} ha usado el {usedPercent} % del límite de este mes"),
                entry(NotificationType.MAP_BUDGET_REACHED, "Presupuesto del mapa agotado", "Mapbox {api} alcanzó su límite; las búsquedas usan resultados guardados hasta {resetTime}"),
                entry(NotificationType.PLACE_EDIT_APPROVED, "Lugar actualizado", "¡Gracias! La información de {placeName} se ha actualizado"),
                entry(NotificationType.PLACE_EDIT_REJECTED, "Sugerencia no aplicada", "Tu sugerencia para {placeName} no se ha aplicado"),
                entry(NotificationType.MEMBER_ACCESS_GRANTED, "Solicitud aceptada", "Se aceptó tu solicitud para unirte a “{tripName}”"),
                entry(NotificationType.MEMBER_JOIN_REJECTED, "Solicitud rechazada", "Se rechazó tu solicitud para unirte a “{tripName}”"),
                entry(NotificationType.MEMBER_ROLE_UPDATED, "Rol actualizado", "{actorName} cambió el rol de {memberName} en “{tripName}” a {role}"),
                entry(NotificationType.GUEST_UPDATED, "Invitado actualizado", "{actorName} cambió el nombre de {previousGuestName} a {guestName} en “{tripName}”"),
                entry(NotificationType.CHECKIN_UPDATED, "Check-in actualizado", "{actorName} actualizó un check-in en “{activityName}” de “{tripName}”"),
                entry(NotificationType.NOTE_UPDATED, "Nota actualizada", "{actorName} actualizó una nota de “{activityName}” en “{tripName}”"),
                entry(NotificationType.MEMORY_ADDED, "Nuevo recuerdo", "{actorName} añadió un recuerdo a “{tripName}”"),
                entry(NotificationType.MEMORY_DELETED, "Recuerdo eliminado", "{actorName} eliminó un recuerdo de “{tripName}”"),
                entry(NotificationType.TRIP_BOOK_UPDATED, "Libro de viaje actualizado", "{actorName} actualizó el libro de viaje de “{tripName}”"),
                entry(NotificationType.TRIP_CLONED, "Viaje copiado", "{actorName} copió tu viaje “{tripName}”"),
                entry(NotificationType.ROUTE_OPTIMIZED, "Ruta lista", "La ruta de “{tripName}” se ha optimizado"),
                entry(NotificationType.TRIP_REMINDER, "Tu viaje se acerca", "“{tripName}” está cerca. Tómate un momento para revisar tu itinerario"),
                entry(NotificationType.TRIP_STARTS_IN_ONE_WEEK, "Falta una semana", "“{tripName}” empieza en una semana. Revisa tu itinerario y tus reservas cuando puedas"),
                entry(NotificationType.TRIP_STARTS_IN_THREE_DAYS, "Faltan tres días", "“{tripName}” empieza en tres días. Revisa tus billetes, el transporte y las reservas"),
                entry(NotificationType.TRIP_STARTS_IN_TWO_DAYS, "Faltan dos días", "“{tripName}” empieza en dos días. Guarda lo que puedas necesitar sin conexión"),
                entry(NotificationType.TRIP_STARTS_IN_ONE_DAY, "Tu viaje empieza mañana", "“{tripName}” empieza mañana. Revisa por última vez tu itinerario y lo esencial"),
                entry(NotificationType.TRIP_STARTS_IN_TWO_HOURS, "Sales en dos horas", "“{tripName}” empieza en dos horas. Revisa tu primera parada y calcula bien el tiempo de traslado"),
                entry(NotificationType.TRIP_STARTED, "Tu viaje empieza", "“{tripName}” ya ha comenzado. Tu itinerario está listo, ¡buen viaje!"),
                entry(NotificationType.ITINERARY_ITEM_PREPARATION, "Prepárate: {itemKindLabel}", "“{itemName}” empieza en {preparationLeadLabel}. {preparationChecklist}"),
                entry(NotificationType.ITINERARY_ITEM_UPCOMING, "{itemKindLabel} en 15 minutos", "“{itemName}” es lo siguiente y empieza en 15 minutos"),
                entry(NotificationType.ITINERARY_ITEM_COMPLETED, "¿Qué tal “{itemName}”?", "Terminaste “{itemName}”. Deja una reseña breve para recordar la experiencia. {nextItemSentence}"),
                entry(NotificationType.TRIP_DAY_ENDED, "Día {dayNumber} completado", "Terminó el día {dayNumber} de “{tripName}”. Haz check-in en los lugares que visitaste y deja una reseña breve mientras lo recuerdas"),
                entry(NotificationType.TRIP_DAY_WEATHER_ALERT, "Pronóstico para mañana: {weatherLabel}", "El día {dayNumber} de “{tripName}” podría verse afectado. Revisa el plan por si quieres cambiarlo"),
                entry(NotificationType.TRIP_ENDED, "Bienvenido de vuelta", "“{tripName}” ha terminado. ¡Esperamos que hayas tenido un viaje maravilloso!"),
                entry(NotificationType.TRIP_SUMMARY, "Tu viaje en resumen", "Gastaste {totalExpense} {currency} en {placesCount} lugares. {debtSummary}"),
                entry(NotificationType.MARKETPLACE_BOOKING_REQUEST, "Nueva solicitud de reserva", "Un huésped envió una solicitud de reserva. Revísala cuando puedas"),
                entry(NotificationType.MARKETPLACE_BOOKING_CONFIRMED, "Reserva confirmada", "Tu reserva está confirmada. ¡Todo listo!"),
                entry(NotificationType.MARKETPLACE_BOOKING_DECLINED, "Reserva no confirmada", "No se pudo confirmar tu solicitud de reserva. Abre la app para ver otras opciones"),
                entry(NotificationType.MARKETPLACE_BOOKING_EXPIRED, "Solicitud de reserva caducada", "El socio no respondió a tiempo, así que tu solicitud se liberó. Puedes reservar de nuevo o elegir otra opción"),
                entry(NotificationType.MARKETPLACE_BOOKING_CANCELLED_BY_GUEST, "Un huésped canceló una reserva", "Un huésped canceló {bookingCode}. La disponibilidad se ha liberado"),
                entry(NotificationType.MARKETPLACE_BOOKING_UPDATED, "Reserva actualizada", "{bookingCode} ahora está {statusLabel}. Abre Mis reservas para ver los detalles"),
                entry(NotificationType.MARKETPLACE_CHANGE_REQUESTED, "Solicitud de cambio", "Un huésped pidió cambiar {bookingCode}. Revísala antes de que caduque"),
                entry(NotificationType.MARKETPLACE_CHANGE_ANSWERED, "Solicitud de cambio: {decisionLabel}", "Tu solicitud de cambio para {bookingCode}: {decisionLabel}. Abre la reserva para ver los detalles"),
                entry(NotificationType.MARKETPLACE_REVIEW_INVITE, "¿Qué tal tu estancia?", "Tu reserva {bookingCode} ha finalizado. Deja una reseña para ayudar a otros viajeros"),
                entry(NotificationType.MARKETPLACE_MESSAGE, "Nuevo mensaje", "{senderName} te envió un mensaje"),
                entry(NotificationType.CHAT_MENTION, "Te mencionaron", "{senderName} te mencionó en {conversationTitle}"),
                entry(NotificationType.PARTNER_VERIFICATION_DECIDED, "Verificación: {decisionLabel}", "Tu organización {organizationName}: {decisionLabel}. {reason}"),
                entry(NotificationType.PARTNER_VERIFICATION_SUBMITTED, "Verificación de socio enviada", "{organizationName} envió documentos para su verificación"),
                entry(NotificationType.PARTNER_STATEMENT_READY, "Extracto disponible", "Tu extracto de {periodLabel} está listo: {netAmount} después de la comisión. Abre Finanzas para revisarlo"),
                entry(NotificationType.ADMIN_ANNOUNCEMENT, "Anuncio de OnDetour", "{body}"),
                entry(NotificationType.ADMIN_MESSAGE, "Mensaje de OnDetour", "{body}")
        );
    }

    private Map.Entry<NotificationType, NotificationMessage> entry(NotificationType type, String title, String body) {
        return Map.entry(type, new NotificationMessage(title, body));
    }
}
