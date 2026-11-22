package com.ds.goroute.service.place;

import com.ds.goroute.dto.PlacePriceRange;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code places.price_range} text for a suggested range, in the shape the scraper stores from
 * Google ({@code ₫100,000–200,000 per person} minus the trailing words): {@code ₫50,000–100,000},
 * or {@code ₫50,000} when both ends are equal. Only VND is accepted. The reverse reads a stored
 * text back for the edit form; Google's price levels ({@code ₫₫}) have no numbers and read as null.
 */
public final class PlacePriceRangeFormatter {

    public static final String CURRENCY = "VND";
    public static final int MAX_DISPLAY_LENGTH = 50;
    /** Far above any per-person price; stops a typo from producing a 20-digit label. */
    public static final long MAX_AMOUNT = 1_000_000_000L;
    private static final String SYMBOL = "₫";
    private static final String DASH = "–";
    private static final Pattern STORED = Pattern.compile(
            "^\\s*₫\\s*([\\d.,]+)\\s*(?:[-–—]\\s*₫?\\s*([\\d.,]+))?.*$");

    private PlacePriceRangeFormatter() {
    }

    /**
     * @throws IllegalArgumentException unless {@code 0 < min <= max <= MAX_AMOUNT} in VND
     */
    public static String format(PlacePriceRange range) {
        if (range == null || range.getMin() == null || range.getMax() == null) {
            throw new IllegalArgumentException("priceRange needs min and max");
        }
        if (range.getCurrency() != null && !range.getCurrency().isBlank()
                && !CURRENCY.equalsIgnoreCase(range.getCurrency().trim())) {
            throw new IllegalArgumentException("priceRange currency must be VND");
        }
        long min = range.getMin();
        long max = range.getMax();
        if (min <= 0 || min > max || max > MAX_AMOUNT) {
            throw new IllegalArgumentException("priceRange needs 0 < min <= max");
        }
        String display = min == max
                ? SYMBOL + grouped(min)
                : SYMBOL + grouped(min) + DASH + grouped(max);
        if (display.length() > MAX_DISPLAY_LENGTH) {
            throw new IllegalArgumentException("priceRange is too long");
        }
        return display;
    }

    /** The range a stored text names, or null when it holds no amounts (a price level, blank, other currency). */
    public static PlacePriceRange parse(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        Matcher matcher = STORED.matcher(stored);
        if (!matcher.matches()) {
            return null;
        }
        Long min = amount(matcher.group(1));
        Long max = matcher.group(2) == null ? min : amount(matcher.group(2));
        if (min == null || max == null || min <= 0 || min > max) {
            return null;
        }
        return PlacePriceRange.builder().min(min).max(max).currency(CURRENCY).build();
    }

    private static Long amount(String text) {
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isEmpty() || digits.length() > 12) {
            return null;
        }
        return Long.parseLong(digits);
    }

    private static String grouped(long amount) {
        DecimalFormat format = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return format.format(amount);
    }
}
