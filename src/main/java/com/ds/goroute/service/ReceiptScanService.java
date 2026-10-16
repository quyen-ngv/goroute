package com.ds.goroute.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.ReceiptScanAccessResponse;
import com.ds.goroute.dto.response.ReceiptScanResponse;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.AiApiCallMapper;
import com.ds.goroute.repository.AiTripRepository;
import com.ds.goroute.thirdparty.ai.AiCallScope;
import com.ds.goroute.thirdparty.openai.OpenAiVisionClient;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.ExpenseCategory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ds.goroute.service.time.UserTimeZones;
import com.ds.goroute.utils.AppZones;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Reads a receipt photo into the fields of a new expense. Nothing is stored: the app shows the
 * result for the user to correct and saves the expense itself. The daily allowance is counted
 * from ai_api_calls (feature {@value #FEATURE}, status SUCCEEDED), so a provider failure is free.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptScanService {

    static final String FEATURE = "RECEIPT_SCAN";
    static final long MAX_IMAGE_BYTES = 6L * 1024 * 1024;
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000000");
    private static final Pattern ISO_CURRENCY = Pattern.compile("[A-Z]{3}");
    private static final int MAX_MERCHANT_CHARS = 120;
    private static final int MAX_CANDIDATES = 3;

    static final String SYSTEM_PROMPT = """
            You read one photo of a receipt, bill, invoice, ticket or payment confirmation screen \
            and report what the customer paid. Fields:
            - isReceipt: false when the photo shows no bill or payment at all.
            - total: the final amount the customer paid, after discounts and including tax, \
            service charge and tip when printed. A number, no separators. null if unreadable.
            - currency: ISO 4217 code from symbols or words on the receipt (₫, đ, VND -> VND; \
            ฿ -> THB; ¥ with Japanese text -> JPY, with Chinese text -> CNY; ₩ -> KRW; $ -> USD \
            unless the country is clear, e.g. S$ -> SGD). null if the receipt gives no clue.
            - merchant: the shop, restaurant, hotel or operator name as printed, without address \
            or legal suffixes. null if absent.
            - date: the purchase date as YYYY-MM-DD. Day comes before month on Vietnamese and \
            most non-US receipts. null if absent.
            - category: one of FOOD, TRANSPORT, HOTEL, ACTIVITY, SHOPPING, OTHER. Restaurants, \
            cafes, bars, groceries for meals -> FOOD; taxi, Grab, fuel, tolls, parking, flights, \
            trains, buses, ferries -> TRANSPORT; hotels, homestays, hostels -> HOTEL; tickets, \
            tours, entrance fees, spa, rentals for an activity -> ACTIVITY; shops, markets, \
            souvenirs -> SHOPPING.
            - candidates: up to 3 OTHER printed amounts that a person might mistake for the \
            total (subtotal, amount before tip, amount after tip), each {"label","amount"} with \
            the label in the receipt's language. Never repeat the total.
            Vietnamese receipts: "Tổng cộng", "Tổng tiền", "Thành tiền" of the whole bill, \
            "Tổng thanh toán", "Khách phải trả", "Cần thanh toán" mark the total. "Tiền khách \
            đưa", "Tiền mặt", "Khách trả", "Tiền thối", "Tiền thừa" are cash handed over or \
            change, never the total. In VND "450.000", "450,000" and "450K" all mean 450000; VND, \
            JPY and KRW have no decimals.
            Output: {"isReceipt":bool,"total":number|null,"currency":string|null,\
            "merchant":string|null,"date":string|null,"category":string|null,\
            "candidates":[{"label":string,"amount":number}]}""";

    private final OpenAiVisionClient visionClient;
    private final BusinessConfigService businessConfig;
    private final BetaAccessService betaAccess;
    private final AiTripRepository subscriptions;
    private final AiApiCallMapper aiApiCallMapper;
    private final ObjectMapper objectMapper;
    private final UserTimeZones userTimeZones;

    /** Replaced in tests; ai_api_calls timestamps are written in the JVM default zone. */
    Clock clock = Clock.systemDefaultZone();

    public ReceiptScanAccessResponse access(UUID userId) {
        return ReceiptScanAccessResponse.builder()
                .available(isAvailable(userId))
                .dailyLimit(dailyLimit(userId))
                .usedToday(usedToday(userId))
                .build();
    }

    /**
     * @param currencyHint the currency the user is most likely paying in (the trip's); used
     *                     only when the receipt itself gives no clue
     */
    public ReceiptScanResponse scan(UUID userId, MultipartFile file, String currencyHint) {
        if (!isAvailable(userId)) {
            throw new BusinessException(ErrorConstant.EXPENSE_RECEIPT_SCAN_UNAVAILABLE, HttpStatus.FORBIDDEN);
        }
        int limit = dailyLimit(userId);
        if (usedToday(userId) >= limit) {
            throw new BusinessException(ErrorConstant.EXPENSE_RECEIPT_SCAN_DAILY_LIMIT, HttpStatus.TOO_MANY_REQUESTS);
        }
        byte[] image = readImage(file);
        String mimeType = mimeTypeOf(image);
        String hint = normalizeCurrency(currencyHint);

        Optional<String> raw = AiCallScope.call(FEATURE, "SCAN", userId, null,
                () -> visionClient.completeJson(SYSTEM_PROMPT, userPrompt(hint), image, mimeType));

        ReceiptScanResponse result = raw.map(json -> parse(json, hint)).orElseGet(ReceiptScanService::unreadable);
        result.setDailyLimit(limit);
        result.setUsedToday(usedToday(userId));
        return result;
    }

    boolean isAvailable(UUID userId) {
        if (userId == null || !visionClient.isConfigured()) return false;
        return businessConfig.getBoolean(BusinessConfigKey.EXPENSE_RECEIPT_SCAN_ENABLED) || betaAccess.isBetaUser(userId);
    }

    int dailyLimit(UUID userId) {
        String tier = subscriptions.getSubscriptionTier(userId);
        boolean pro = tier != null && "PRO".equals(tier.trim().toUpperCase(Locale.ROOT));
        return businessConfig.getInt(pro
                ? BusinessConfigKey.EXPENSE_RECEIPT_SCAN_PRO_DAILY
                : BusinessConfigKey.EXPENSE_RECEIPT_SCAN_FREE_DAILY);
    }

    int usedToday(UUID userId) {
        return (int) aiApiCallMapper.countSucceededSince(userId, FEATURE, startOfToday(userId));
    }

    /** The user's local midnight, expressed in the clock's zone. */
    LocalDateTime startOfToday(UUID userId) {
        ZoneId zone = userTimeZones.zoneFor(userId);
        ZonedDateTime localMidnight = ZonedDateTime.now(clock.withZone(zone)).truncatedTo(ChronoUnit.DAYS);
        return localMidnight.withZoneSameInstant(clock.getZone()).toLocalDateTime();
    }

    static String userPrompt(String currencyHint) {
        return currencyHint == null
                ? "Read this receipt."
                : "Read this receipt. If it shows no currency at all, the customer was most likely paying in "
                + currencyHint + ".";
    }

    ReceiptScanResponse parse(String json, String currencyHint) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (IOException e) {
            log.warn("Receipt scan returned invalid JSON");
            return unreadable();
        }
        if (root == null || !root.isObject() || !root.path("isReceipt").asBoolean(true)) {
            return unreadable();
        }
        String currency = Optional.ofNullable(normalizeCurrency(text(root.path("currency")))).orElse(currencyHint);
        BigDecimal total = amount(root.path("total"), currency);

        List<ReceiptScanResponse.Candidate> candidates = new ArrayList<>();
        for (JsonNode node : root.path("candidates")) {
            if (candidates.size() >= MAX_CANDIDATES) break;
            BigDecimal value = amount(node.path("amount"), currency);
            if (value == null || (total != null && value.compareTo(total) == 0)) continue;
            if (candidates.stream().anyMatch(c -> c.getAmount().compareTo(value) == 0)) continue;
            candidates.add(ReceiptScanResponse.Candidate.builder()
                    .label(truncate(text(node.path("label")), 40))
                    .amount(value)
                    .build());
        }

        return ReceiptScanResponse.builder()
                .readable(total != null)
                .amount(total)
                .currency(currency)
                .merchant(truncate(text(root.path("merchant")), MAX_MERCHANT_CHARS))
                .spentOn(date(text(root.path("date"))))
                .category(category(text(root.path("category"))))
                .candidates(candidates)
                .build();
    }

    private static ReceiptScanResponse unreadable() {
        return ReceiptScanResponse.builder().readable(false).candidates(List.of()).build();
    }

    private static byte[] readImage(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException(ErrorConstant.EXPENSE_RECEIPT_SCAN_INVALID_IMAGE, HttpStatus.BAD_REQUEST);
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorConstant.EXPENSE_RECEIPT_SCAN_INVALID_IMAGE, HttpStatus.BAD_REQUEST);
        }
    }

    /** By content, not by the client's word; the provider takes JPEG, PNG and WebP. */
    static String mimeTypeOf(byte[] b) {
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        throw new BusinessException(ErrorConstant.EXPENSE_RECEIPT_SCAN_INVALID_IMAGE, HttpStatus.BAD_REQUEST);
    }

    static String normalizeCurrency(String value) {
        if (value == null) return null;
        String code = value.trim().toUpperCase(Locale.ROOT);
        if (!ISO_CURRENCY.matcher(code).matches()) return null;
        try {
            Currency.getInstance(code);
            return code;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Positive, below the cap, rounded to the currency's minor unit (none for VND, JPY, KRW). */
    static BigDecimal amount(JsonNode node, String currency) {
        BigDecimal value;
        if (node.isNumber()) {
            value = node.decimalValue();
        } else if (node.isTextual()) {
            try {
                value = new BigDecimal(node.asText().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        } else {
            return null;
        }
        if (value.signum() <= 0 || value.compareTo(MAX_AMOUNT) >= 0) return null;
        int digits = 2;
        if (currency != null) {
            int minor = Currency.getInstance(currency).getDefaultFractionDigits();
            digits = minor < 0 ? 2 : minor;
        }
        return value.setScale(digits, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    /** A date in the future or older than two years is a misread, not a purchase date. */
    LocalDate date(String value) {
        if (value == null) return null;
        try {
            LocalDate date = LocalDate.parse(value.trim());
            // UTC today plus one day of slack covers every zone the receipt could come from.
            LocalDate today = LocalDate.now(clock.withZone(AppZones.UTC));
            return date.isAfter(today.plusDays(1)) || date.isBefore(today.minusYears(2)) ? null : date;
        } catch (Exception e) {
            return null;
        }
    }

    static String category(String value) {
        if (value == null) return null;
        try {
            return ExpenseCategory.valueOf(value.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        String value = node.asText().trim();
        return value.isEmpty() ? null : value;
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max).trim();
    }
}
