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
            "ja", japanese(),
            "ko", korean()
    );

    public NotificationMessage render(NotificationType type, Map<String, Object> data, String language) {
        String lang = NotificationLanguage.normalize(language);
        Map<String, Object> normalizedData = normalizeData(data, lang);
        if (isAdminNotification(type)) {
            NotificationMessage adminMessage = renderAdminMessage(normalizedData);
            if (adminMessage != null) {
                return adminMessage;
            }
        }

        NotificationMessage template = templates
                .getOrDefault(lang, templates.get(NotificationLanguage.DEFAULT))
                .get(type);

        if (template == null) {
            template = templates.get(NotificationLanguage.DEFAULT).get(type);
        }
        if (template == null) {
            return new NotificationMessage("Trip update", "There is a new update in your trip");
        }

        return new NotificationMessage(
                interpolate(template.title(), normalizedData),
                interpolate(template.body(), normalizedData)
        );
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
        Object isPaid = normalized.get("isPaid");
        if (isPaid instanceof Boolean paid) {
            normalized.put("paidStatus", paidStatus(paid, language));
        } else if (isPaid != null) {
            normalized.put("paidStatus", isPaid);
        }
        return normalized;
    }

    private String paidStatus(boolean paid, String language) {
        return switch (language) {
            case "vi" -> paid ? "da thanh toan" : "chua thanh toan";
            case "ja" -> paid ? "shiharaizumi" : "mihiharai";
            case "ko" -> paid ? "gyeolje wanlyo" : "migyeolje";
            default -> paid ? "paid" : "unpaid";
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
                entry(NotificationType.EXPENSE_ADDED, "New expense added", "{actorName} added \"{expenseName}\" {amount} {currency} to {tripName}"),
                entry(NotificationType.EXPENSE_UPDATED, "Expense updated", "{actorName} updated \"{expenseName}\" in {tripName}"),
                entry(NotificationType.EXPENSE_DELETED, "Expense deleted", "{actorName} deleted \"{expenseName}\" from {tripName}"),
                entry(NotificationType.ACTIVITY_ADDED, "Activity added", "{actorName} added \"{activityName}\" to {tripName}"),
                entry(NotificationType.ACTIVITY_UPDATED, "Activity updated", "{actorName} updated \"{activityName}\" in {tripName}"),
                entry(NotificationType.ACTIVITY_DELETED, "Activity deleted", "{actorName} deleted \"{activityName}\" from {tripName}"),
                entry(NotificationType.MEMBER_ADDED, "New member", "{actorName} added {newMemberName} to {tripName}"),
                entry(NotificationType.MEMBER_REMOVED, "Member removed", "{actorName} removed {removedMemberName} from {tripName}"),
                entry(NotificationType.MEMBER_ACCEPTED, "Member accepted", "{memberName} joined {tripName}"),
                entry(NotificationType.MEMBER_LEFT, "Member left", "{actorName} left {tripName}"),
                entry(NotificationType.GUEST_LINKED, "Guest linked", "{guestName} has been linked to {linkedUserName} in {tripName}"),
                entry(NotificationType.TRIP_UPDATED, "Trip updated", "{actorName} updated trip {tripName}"),
                entry(NotificationType.TRIP_DELETED, "Trip deleted", "{actorName} deleted trip {tripName}"),
                entry(NotificationType.PAYMENT_MARKED, "Payment updated", "{payerName} updated {payeeName}'s payment of {amount} {currency} for \"{expenseDescription}\""),
                entry(NotificationType.PAYMENT_ALL_MARKED, "Expense payments updated", "{actorName} marked payments for \"{expenseDescription}\" as {paidStatus}"),
                entry(NotificationType.PAYMENT_TRIP_MARKED, "Trip payments updated", "{actorName} marked payments in {tripName} as {paidStatus}"),
                entry(NotificationType.CHECKIN, "Check-in", "{actorName} checked in at \"{activityName}\" in {tripName}"),
                entry(NotificationType.NOTE_ADDED, "Note added", "{actorName} added a note to {activityName} {tripName}"),
                entry(NotificationType.NOTE_DELETED, "Note deleted", "{actorName} deleted a note from {activityName} {tripName}"),
                entry(NotificationType.COMMENT_ADDED, "Comment added", "{actorName} commented on \"{activityName}\" in {tripName}"),
                entry(NotificationType.COMMENT_DELETED, "Comment deleted", "{actorName} deleted a comment from \"{activityName}\" in {tripName}"),
                entry(NotificationType.ADMIN_ANNOUNCEMENT, "TripMind Announcement", "{body}"),
                entry(NotificationType.ADMIN_MESSAGE, "Message from TripMind", "{body}")
        );
    }

    private Map<NotificationType, NotificationMessage> vietnamese() {
        return Map.ofEntries(
                entry(NotificationType.EXPENSE_ADDED, "Da them chi phi", "{actorName} da them \"{expenseName}\" {amount} {currency} vao {tripName}"),
                entry(NotificationType.EXPENSE_UPDATED, "Da cap nhat chi phi", "{actorName} da cap nhat \"{expenseName}\" trong {tripName}"),
                entry(NotificationType.EXPENSE_DELETED, "Da xoa chi phi", "{actorName} da xoa \"{expenseName}\" khoi {tripName}"),
                entry(NotificationType.ACTIVITY_ADDED, "Da them hoat dong", "{actorName} da them \"{activityName}\" vao {tripName}"),
                entry(NotificationType.ACTIVITY_UPDATED, "Da cap nhat hoat dong", "{actorName} da cap nhat \"{activityName}\" trong {tripName}"),
                entry(NotificationType.ACTIVITY_DELETED, "Da xoa hoat dong", "{actorName} da xoa \"{activityName}\" khoi {tripName}"),
                entry(NotificationType.MEMBER_ADDED, "Thanh vien moi", "{actorName} da them {newMemberName} vao {tripName}"),
                entry(NotificationType.MEMBER_REMOVED, "Da xoa thanh vien", "{actorName} da xoa {removedMemberName} khoi {tripName}"),
                entry(NotificationType.MEMBER_ACCEPTED, "Thanh vien da tham gia", "{memberName} da tham gia {tripName}"),
                entry(NotificationType.MEMBER_LEFT, "Thanh vien da roi di", "{actorName} da roi khoi {tripName}"),
                entry(NotificationType.GUEST_LINKED, "Da lien ket khach", "{guestName} da duoc lien ket voi {linkedUserName} trong {tripName}"),
                entry(NotificationType.TRIP_UPDATED, "Da cap nhat chuyen di", "{actorName} da cap nhat chuyen di {tripName}"),
                entry(NotificationType.TRIP_DELETED, "Da xoa chuyen di", "{actorName} da xoa chuyen di {tripName}"),
                entry(NotificationType.PAYMENT_MARKED, "Da cap nhat thanh toan", "{payerName} da cap nhat thanh toan {amount} {currency} cua {payeeName} cho \"{expenseDescription}\""),
                entry(NotificationType.PAYMENT_ALL_MARKED, "Da cap nhat thanh toan chi phi", "{actorName} da danh dau thanh toan cho \"{expenseDescription}\" la {paidStatus}"),
                entry(NotificationType.PAYMENT_TRIP_MARKED, "Da cap nhat thanh toan chuyen di", "{actorName} da danh dau thanh toan trong {tripName} la {paidStatus}"),
                entry(NotificationType.CHECKIN, "Check-in", "{actorName} da check-in tai \"{activityName}\" trong {tripName}"),
                entry(NotificationType.NOTE_ADDED, "Da them ghi chu", "{actorName} da them ghi chu vao {activityName} {tripName}"),
                entry(NotificationType.NOTE_DELETED, "Da xoa ghi chu", "{actorName} da xoa ghi chu khoi {activityName} {tripName}"),
                entry(NotificationType.COMMENT_ADDED, "Da them binh luan", "{actorName} da binh luan o \"{activityName}\" trong {tripName}"),
                entry(NotificationType.COMMENT_DELETED, "Da xoa binh luan", "{actorName} da xoa binh luan khoi \"{activityName}\" trong {tripName}"),
                entry(NotificationType.ADMIN_ANNOUNCEMENT, "Thong bao tu TripMind", "{body}"),
                entry(NotificationType.ADMIN_MESSAGE, "Tin nhan tu TripMind", "{body}")
        );
    }

    private Map<NotificationType, NotificationMessage> japanese() {
        return Map.ofEntries(
                entry(NotificationType.EXPENSE_ADDED, "Shishutsu ga tsuika saremashita", "{actorName}ga{tripName}ni\"{expenseName}\"{amount} {currency}wo tsuika shimashita"),
                entry(NotificationType.EXPENSE_UPDATED, "Shishutsu ga koshin saremashita", "{actorName}ga{tripName}no\"{expenseName}\"wo koshin shimashita"),
                entry(NotificationType.EXPENSE_DELETED, "Shishutsu ga sakujo saremashita", "{actorName}ga{tripName}kara\"{expenseName}\"wo sakujo shimashita"),
                entry(NotificationType.ACTIVITY_ADDED, "Yotei ga tsuika saremashita", "{actorName}ga{tripName}ni\"{activityName}\"wo tsuika shimashita"),
                entry(NotificationType.ACTIVITY_UPDATED, "Yotei ga koshin saremashita", "{actorName}ga{tripName}no\"{activityName}\"wo koshin shimashita"),
                entry(NotificationType.ACTIVITY_DELETED, "Yotei ga sakujo saremashita", "{actorName}ga{tripName}kara\"{activityName}\"wo sakujo shimashita"),
                entry(NotificationType.MEMBER_ADDED, "Atarashii menba", "{actorName}ga{newMemberName}wo{tripName}ni tsuika shimashita"),
                entry(NotificationType.MEMBER_REMOVED, "Menba ga sakujo saremashita", "{actorName}ga{removedMemberName}wo{tripName}kara sakujo shimashita"),
                entry(NotificationType.MEMBER_ACCEPTED, "Menba ga sanka shimashita", "{memberName}ga{tripName}ni sanka shimashita"),
                entry(NotificationType.MEMBER_LEFT, "Menba ga taishutsu shimashita", "{actorName}ga{tripName}kara taishutsu shimashita"),
                entry(NotificationType.GUEST_LINKED, "Gesuto ga rinku saremashita", "{guestName}ga{tripName}de{linkedUserName}ni rinku saremashita"),
                entry(NotificationType.TRIP_UPDATED, "Ryoko ga koshin saremashita", "{actorName}ga ryoko{tripName}wo koshin shimashita"),
                entry(NotificationType.TRIP_DELETED, "Ryoko ga sakujo saremashita", "{actorName}ga ryoko{tripName}wo sakujo shimashita"),
                entry(NotificationType.PAYMENT_MARKED, "Shiharai ga koshin saremashita", "{payerName}ga\"{expenseDescription}\"no{payeeName}no shiharai{amount} {currency}wo koshin shimashita"),
                entry(NotificationType.PAYMENT_ALL_MARKED, "Shiharai ga koshin saremashita", "{actorName}ga\"{expenseDescription}\"no shiharaiwo{paidStatus}ni shimashita"),
                entry(NotificationType.PAYMENT_TRIP_MARKED, "Ryoko no shiharai ga koshin saremashita", "{actorName}ga{tripName}no shiharaiwo{paidStatus}ni shimashita"),
                entry(NotificationType.CHECKIN, "Chekkuin", "{actorName}ga{tripName}no\"{activityName}\"ni chekkuin shimashita"),
                entry(NotificationType.NOTE_ADDED, "Noto ga tsuika saremashita", "{actorName}ga{activityName} {tripName}ni notowo tsuika shimashita"),
                entry(NotificationType.NOTE_DELETED, "Noto ga sakujo saremashita", "{actorName}ga{activityName} {tripName}kara notowo sakujo shimashita"),
                entry(NotificationType.COMMENT_ADDED, "Komento ga tsuika saremashita", "{actorName}ga{tripName}no\"{activityName}\"ni komento shimashita"),
                entry(NotificationType.COMMENT_DELETED, "Komento ga sakujo saremashita", "{actorName}ga{tripName}no\"{activityName}\"kara komentowosakujo shimashita"),
                entry(NotificationType.ADMIN_ANNOUNCEMENT, "TripMind kara no oshirase", "{body}"),
                entry(NotificationType.ADMIN_MESSAGE, "TripMind kara no messji", "{body}")
        );
    }

    private Map<NotificationType, NotificationMessage> korean() {
        return Map.ofEntries(
                entry(NotificationType.EXPENSE_ADDED, "Biyongi chugadoeeossseubnida", "{actorName}nimi {tripName}e \"{expenseName}\" {amount} {currency}reul chugahaessseubnida"),
                entry(NotificationType.EXPENSE_UPDATED, "Biyongi sujungdoeeossseubnida", "{actorName}nimi {tripName}ui \"{expenseName}\"eul sujunghaessseubnida"),
                entry(NotificationType.EXPENSE_DELETED, "Biyongi sakjedoeeossseubnida", "{actorName}nimi {tripName}eseo \"{expenseName}\"eul sakjehaessseubnida"),
                entry(NotificationType.ACTIVITY_ADDED, "Iljeongi chugadoeeossseubnida", "{actorName}nimi {tripName}e \"{activityName}\"eul chugahaessseubnida"),
                entry(NotificationType.ACTIVITY_UPDATED, "Iljeongi sujungdoeeossseubnida", "{actorName}nimi {tripName}ui \"{activityName}\"eul sujunghaessseubnida"),
                entry(NotificationType.ACTIVITY_DELETED, "Iljeongi sakjedoeeossseubnida", "{actorName}nimi {tripName}eseo \"{activityName}\"eul sakjehaessseubnida"),
                entry(NotificationType.MEMBER_ADDED, "Sae membeo", "{actorName}nimi {newMemberName}nimeul {tripName}e chugahaessseubnida"),
                entry(NotificationType.MEMBER_REMOVED, "Membeoga sakjedoeeossseubnida", "{actorName}nimi {tripName}eseo {removedMemberName}nimeul sakjehaessseubnida"),
                entry(NotificationType.MEMBER_ACCEPTED, "Membeoga chamyeohaessseubnida", "{memberName}nimi {tripName}e chamyeohaessseubnida"),
                entry(NotificationType.MEMBER_LEFT, "Membeoga nagassseubnida", "{actorName}nimi {tripName}eseo nagassseubnida"),
                entry(NotificationType.GUEST_LINKED, "Geseuti ga yeongyeoldoeeossseubnida", "{guestName}nimi {tripName}eseo {linkedUserName}nimgwa yeongyeoldoeeossseubnida"),
                entry(NotificationType.TRIP_UPDATED, "Yeohaengi sujungdoeeossseubnida", "{actorName}nimi {tripName} yeohaengeul sujunghaessseubnida"),
                entry(NotificationType.TRIP_DELETED, "Yeohaengi sakjedoeeossseubnida", "{actorName}nimi {tripName} yeohaengeul sakjehaessseubnida"),
                entry(NotificationType.PAYMENT_MARKED, "Gyeolje ga eopdeiteutodoeeossseubnida", "{payerName}nimi \"{expenseDescription}\"ui {payeeName}nim gyeolje {amount} {currency}reul eopdeiteuteuhaessseubnida"),
                entry(NotificationType.PAYMENT_ALL_MARKED, "Biyong gyeolje ga eopdeiteutodoeeossseubnida", "{actorName}nimi \"{expenseDescription}\" gyeoljereul {paidStatus}(euro pyosihyessseubnida"),
                entry(NotificationType.PAYMENT_TRIP_MARKED, "Yeohaeng gyeolje ga eopdeiteutodoeeossseubnida", "{actorName}nimi {tripName} gyeoljereul {paidStatus}(euro pyosihyessseubnida"),
                entry(NotificationType.CHECKIN, "Chekeu-in", "{actorName}nimi {tripName}ui \"{activityName}\"e chekeu-inhaessseubnida"),
                entry(NotificationType.NOTE_ADDED, "Noteuga chugadoeeossseubnida", "{actorName}nimi {activityName} {tripName}e notereul chugahaessseubnida"),
                entry(NotificationType.NOTE_DELETED, "Noteuga sakjedoeeossseubnida", "{actorName}nimi {activityName} {tripName}eseo notereul sakjehaessseubnida"),
                entry(NotificationType.COMMENT_ADDED, "Daetgeuli chugadoeeossseubnida", "{actorName}nimi {tripName}ui \"{activityName}\"e daetgeuleul namgyessseubnida"),
                entry(NotificationType.COMMENT_DELETED, "Daetgeuli sakjedoeeossseubnida", "{actorName}nimi {tripName}ui \"{activityName}\"eseo daetgeuleul sakjehaessseubnida"),
                entry(NotificationType.ADMIN_ANNOUNCEMENT, "TripMind gongjisahang", "{body}"),
                entry(NotificationType.ADMIN_MESSAGE, "TripMind messiji", "{body}")
        );
    }

    private Map.Entry<NotificationType, NotificationMessage> entry(NotificationType type, String title, String body) {
        return Map.entry(type, new NotificationMessage(title, body));
    }
}
