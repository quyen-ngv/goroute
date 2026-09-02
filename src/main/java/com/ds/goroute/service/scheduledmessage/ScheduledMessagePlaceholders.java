package com.ds.goroute.service.scheduledmessage;

import com.ds.goroute.entity.ScheduledMessageTarget;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Placeholder substitution for scheduled guest messages.
 *
 * <p>Pure and total on purpose: copy must never stop a message. A placeholder the rule author
 * invented, or one this booking has no value for (a stay has no {@code slotStartsAt}), is left in
 * the body verbatim and the message still goes out. The alternative — refusing to send, or silently
 * dropping the token — is worse for a guest who is standing at the door waiting for a door code.
 */
public final class ScheduledMessagePlaceholders {
    /** The tokens the partner console advertises. Anything else survives untouched. */
    public static final List<String> SUPPORTED = List.of(
            "guestName", "bookingCode", "propertyName", "checkInDate", "checkOutDate", "slotStartsAt");

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private ScheduledMessagePlaceholders() { }

    /** Substitutes every {@code {token}} this booking has a value for; leaves the rest as written. */
    public static String apply(String body, ScheduledMessageTarget target) {
        if (body == null || body.isBlank() || target == null) return body;
        String result = body;
        for (Map.Entry<String, String> entry : values(target).entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    /** The tokens that resolve for this booking. A token with no value is absent, not empty. */
    public static Map<String, String> values(ScheduledMessageTarget target) {
        Map<String, String> values = new LinkedHashMap<>();
        put(values, "guestName", target.getGuestName());
        put(values, "bookingCode", target.getBookingCode());
        put(values, "propertyName", target.getPropertyName());
        if (target.getCheckInDate() != null) values.put("checkInDate", DATE.format(target.getCheckInDate()));
        if (target.getCheckOutDate() != null) values.put("checkOutDate", DATE.format(target.getCheckOutDate()));
        if (target.getSlotStartsAt() != null) values.put("slotStartsAt", DATE_TIME.format(target.getSlotStartsAt()));
        return values;
    }

    private static void put(Map<String, String> values, String key, String value) {
        if (value != null && !value.isBlank()) values.put(key, value.trim());
    }
}
