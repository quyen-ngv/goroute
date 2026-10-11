package com.ds.goroute.service.mapsearch;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/** Text helpers shared by the map caches: one normal form for names and queries, and cache keys. */
public final class MapText {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern NON_LATIN = Pattern.compile("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}]");

    private MapText() {
    }

    /**
     * Lower case, accents and Vietnamese đ removed, punctuation turned into single spaces:
     * "Chợ Bến Thành" and "cho ben  thanh" become the same string.
     */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String folded = text.replace('đ', 'd').replace('Đ', 'D');
        String decomposed = Normalizer.normalize(folded, Normalizer.Form.NFD);
        String stripped = MARKS.matcher(decomposed).replaceAll("");
        return NOT_WORD.matcher(stripped.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    /** True when the text has Chinese, Japanese or Korean characters. */
    public static boolean hasCjk(String text) {
        return text != null && NON_LATIN.matcher(text).find();
    }

    /** SHA-256 hex of the parts joined by '|': a fixed-length key for any request. */
    public static String key(Object... parts) {
        StringBuilder joined = new StringBuilder();
        for (Object part : parts) {
            joined.append(part == null ? "" : part).append('|');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(joined.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is always available", exception);
        }
    }
}
