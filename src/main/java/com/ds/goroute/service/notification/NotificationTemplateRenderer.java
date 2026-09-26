package com.ds.goroute.service.notification;

import com.ds.goroute.type.NotificationType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class NotificationTemplateRenderer {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_]+)}");

    private final Map<String, Map<NotificationType, NotificationMessage>> templates = Map.of(
            "en", english(),
            "vi", vietnamese(),
            "hi", hindi(),
            "ja", japanese(),
            "ko", korean(),
            "ru", russian(),
            "th", thai(),
            "zh-TW", traditionalChinese()
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
            return withLocalizedPunctuation(renderAiTripCreated(lang), lang);
        }
        if (type == NotificationType.SOCIAL_LIKE || type == NotificationType.SOCIAL_COMMENT) {
            return withLocalizedPunctuation(renderSocialInteraction(type, normalizedData, lang), lang);
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
            default -> new NotificationMessage("Trip update", "There is a new update in your trip");
        };
    }

    private NotificationMessage withLocalizedPunctuation(NotificationMessage message, String language) {
        String punctuation = switch (language) {
            case "ja", "zh-TW" -> "。";
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
                title != null ? title : "TripMind Announcement",
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
            default -> new NotificationMessage(
                    "Places extracted",
                    "Places extracted from " + platformName + ": " + placeCount
                            + ". Open Saved Places to review the result");
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
            default -> new NotificationMessage("Itinerary created", "Your itinerary from the video is ready. Open the trip to review it");
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
                default -> "bình luận";
            };
            case "hi" -> switch (target) {
                case "TRIP" -> "यात्रा";
                case "CHECKIN" -> "चेक-इन";
                case "REVIEW" -> "समीक्षा";
                default -> "टिप्पणी";
            };
            case "ja" -> switch (target) {
                case "TRIP" -> "旅行";
                case "CHECKIN" -> "チェックイン";
                case "REVIEW" -> "レビュー";
                default -> "コメント";
            };
            case "ko" -> switch (target) {
                case "TRIP" -> "여행";
                case "CHECKIN" -> "체크인";
                case "REVIEW" -> "리뷰";
                default -> "댓글";
            };
            case "ru" -> switch (target) {
                case "TRIP" -> "поездку";
                case "CHECKIN" -> "отметку о посещении";
                case "REVIEW" -> "отзыв";
                default -> "комментарий";
            };
            case "th" -> switch (target) {
                case "TRIP" -> "ทริป";
                case "CHECKIN" -> "เช็กอิน";
                case "REVIEW" -> "รีวิว";
                default -> "ความคิดเห็น";
            };
            case "zh-TW" -> switch (target) {
                case "TRIP" -> "行程";
                case "CHECKIN" -> "打卡";
                case "REVIEW" -> "評價";
                default -> "留言";
            };
            default -> switch (target) {
                case "TRIP" -> "trip";
                case "CHECKIN" -> "check-in";
                case "REVIEW" -> "review";
                default -> "comment";
            };
        };
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
                entry(NotificationType.ADMIN_ANNOUNCEMENT, "TripMind Announcement", "{body}"),
                entry(NotificationType.ADMIN_MESSAGE, "Message from TripMind", "{body}")
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
                entry(NotificationType.TRIP_ENDED, "Chuyến đi đã khép lại", "“{tripName}” đã kết thúc. Cảm ơn bạn đã đồng hành cùng TripMind!"),
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
                entry(NotificationType.ADMIN_ANNOUNCEMENT, "Thông báo từ TripMind", "{body}"),
                entry(NotificationType.ADMIN_MESSAGE, "Tin nhắn từ TripMind", "{body}")
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
                entry(NotificationType.TRIP_ENDED, "おかえりなさい", "「{tripName}」が終了しました。すてきな旅になったことを願っています！"),
                entry(NotificationType.TRIP_SUMMARY, "旅の振り返り", "{placesCount}か所で合計{totalExpense} {currency}を使いました。{debtSummary}"),
                entry(NotificationType.MARKETPLACE_BOOKING_REQUEST, "新しい予約リクエスト", "ゲストから予約リクエストが届きました。内容をご確認ください"),
                entry(NotificationType.MARKETPLACE_BOOKING_CONFIRMED, "予約が確定しました", "予約が確定しました。準備は完了です！"),
                entry(NotificationType.MARKETPLACE_BOOKING_DECLINED, "予約を確定できませんでした", "予約リクエストを確定できませんでした。アプリで別の選択肢をご確認ください"),
                entry(NotificationType.ADMIN_ANNOUNCEMENT, "TripMindからのお知らせ", "{body}"),
                entry(NotificationType.ADMIN_MESSAGE, "TripMindからのメッセージ", "{body}")
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
                entry(NotificationType.TRIP_ENDED, "여행을 마쳤어요", "“{tripName}” 여행이 끝났습니다. 즐거운 여행이었기를 바랍니다!"),
                entry(NotificationType.TRIP_SUMMARY, "여행 돌아보기", "{placesCount}곳에서 총 {totalExpense} {currency}를 사용했습니다. {debtSummary}"),
                entry(NotificationType.MARKETPLACE_BOOKING_REQUEST, "새 예약 요청", "게스트의 예약 요청이 도착했습니다. 내용을 확인해 주세요"),
                entry(NotificationType.MARKETPLACE_BOOKING_CONFIRMED, "예약이 확정됐어요", "예약이 확정되었습니다. 이제 준비가 끝났어요!"),
                entry(NotificationType.MARKETPLACE_BOOKING_DECLINED, "예약을 확정하지 못했어요", "예약 요청을 확정하지 못했습니다. 앱에서 다른 옵션을 확인해 주세요"),
                entry(NotificationType.ADMIN_ANNOUNCEMENT, "TripMind 공지사항", "{body}"),
                entry(NotificationType.ADMIN_MESSAGE, "TripMind 메시지", "{body}")
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
                entry(NotificationType.TRIP_ENDED, "वापसी पर स्वागत है", "“{tripName}” समाप्त हो गई है। आशा है आपकी यात्रा शानदार रही!"),
                entry(NotificationType.TRIP_SUMMARY, "यात्रा की एक झलक", "आपने {placesCount} स्थानों पर {totalExpense} {currency} खर्च किए। {debtSummary}"),
                entry(NotificationType.MARKETPLACE_BOOKING_REQUEST, "नया आरक्षण अनुरोध", "एक अतिथि ने आरक्षण अनुरोध भेजा है। कृपया इसकी जानकारी देखें"),
                entry(NotificationType.MARKETPLACE_BOOKING_CONFIRMED, "आरक्षण की पुष्टि हुई", "आपके आरक्षण की पुष्टि हो गई है। सब तैयार है!"),
                entry(NotificationType.MARKETPLACE_BOOKING_DECLINED, "आरक्षण की पुष्टि नहीं हुई", "आरक्षण अनुरोध की पुष्टि नहीं हो सकी। दूसरे विकल्प देखने के लिए ऐप खोलें"),
                entry(NotificationType.ADMIN_ANNOUNCEMENT, "TripMind की सूचना", "{body}"),
                entry(NotificationType.ADMIN_MESSAGE, "TripMind का संदेश", "{body}")
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
                entry(NotificationType.TRIP_ENDED, "С возвращением", "«{tripName}» завершилась. Надеемся, поездка была замечательной!"),
                entry(NotificationType.TRIP_SUMMARY, "Поездка в цифрах", "Вы потратили {totalExpense} {currency} в {placesCount} местах. {debtSummary}"),
                entry(NotificationType.MARKETPLACE_BOOKING_REQUEST, "Новый запрос на бронирование", "Гость отправил запрос на бронирование. Проверьте детали"),
                entry(NotificationType.MARKETPLACE_BOOKING_CONFIRMED, "Бронирование подтверждено", "Бронирование подтверждено. Всё готово!"),
                entry(NotificationType.MARKETPLACE_BOOKING_DECLINED, "Бронирование не подтверждено", "Не удалось подтвердить запрос. Откройте приложение, чтобы посмотреть другие варианты"),
                entry(NotificationType.ADMIN_ANNOUNCEMENT, "Объявление TripMind", "{body}"),
                entry(NotificationType.ADMIN_MESSAGE, "Сообщение от TripMind", "{body}")
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
                entry(NotificationType.TRIP_ENDED, "ยินดีต้อนรับกลับ", "“{tripName}” สิ้นสุดแล้ว หวังว่าคุณจะมีทริปที่ยอดเยี่ยม!"),
                entry(NotificationType.TRIP_SUMMARY, "ภาพรวมทริป", "คุณใช้จ่าย {totalExpense} {currency} ใน {placesCount} สถานที่ {debtSummary}"),
                entry(NotificationType.MARKETPLACE_BOOKING_REQUEST, "คำขอจองใหม่", "มีคำขอจองใหม่จากผู้เข้าพัก โปรดตรวจสอบรายละเอียด"),
                entry(NotificationType.MARKETPLACE_BOOKING_CONFIRMED, "ยืนยันการจองแล้ว", "การจองของคุณได้รับการยืนยันแล้ว ทุกอย่างพร้อม!"),
                entry(NotificationType.MARKETPLACE_BOOKING_DECLINED, "ยังยืนยันการจองไม่ได้", "ไม่สามารถยืนยันคำขอจองได้ เปิดแอปเพื่อดูตัวเลือกอื่น"),
                entry(NotificationType.ADMIN_ANNOUNCEMENT, "ประกาศจาก TripMind", "{body}"),
                entry(NotificationType.ADMIN_MESSAGE, "ข้อความจาก TripMind", "{body}")
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
                entry(NotificationType.TRIP_ENDED, "歡迎回來", "「{tripName}」已結束，希望你度過了一段美好的旅程！"),
                entry(NotificationType.TRIP_SUMMARY, "旅程回顧", "你在 {placesCount} 個地點共花費 {totalExpense} {currency}。{debtSummary}"),
                entry(NotificationType.MARKETPLACE_BOOKING_REQUEST, "新的預訂請求", "旅客送出了一筆預訂請求，請查看詳情"),
                entry(NotificationType.MARKETPLACE_BOOKING_CONFIRMED, "預訂已確認", "你的預訂已確認，一切都準備好了！"),
                entry(NotificationType.MARKETPLACE_BOOKING_DECLINED, "預訂尚未確認", "目前無法確認這筆預訂，請開啟應用程式查看其他選項"),
                entry(NotificationType.ADMIN_ANNOUNCEMENT, "TripMind 公告", "{body}"),
                entry(NotificationType.ADMIN_MESSAGE, "TripMind 訊息", "{body}")
        );
    }

    private Map.Entry<NotificationType, NotificationMessage> entry(NotificationType type, String title, String body) {
        return Map.entry(type, new NotificationMessage(title, body));
    }
}
