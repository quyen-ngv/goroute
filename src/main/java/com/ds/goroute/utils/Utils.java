package com.ds.goroute.utils;

import com.ds.goroute.constant.Symbol;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.MurmurHash3;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Utils {

    private static final String REGEX_FILTER_KEY =
            "[ : ]+((?=\\[)\\[[^]]*\\]|(?=\\{)\\{[^\\}]*\\}|\\\"[^\"]*\\\"|(\\d+(\\.\\d+)?))";
    private static final String LENGTH_INVALID ="%s is invalid length";
    private static final String NAME_INVALID ="%s is invalid";
    private static final Logger log = LoggerFactory.getLogger(Utils.class);

    static List<String> redactKeys = Collections.unmodifiableList(Arrays.asList(
            "api_key", "api_secret", "otp", "pin", "access_token", "full_name", "phone_number", "email",
            "full_name_edit_counter", "mobile_number", "email_address", "email_preference", "authorization",
            "verified_token", "customer_phone_number", "x-api-secret", "x-api-key", "Authorization", "partner",
            "client_id", "public_key", "private_key", "x-public-key", "x-private-key", "newrelic"));

    public static long genAutoId(String key) {
        if (StringUtils.isBlank(key)) {
            key = UUID.randomUUID().toString();
        }
        byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        long h = MurmurHash3.hash64(bytes);
        return Math.abs(h);
    }

    public static String buildAddress(String street, String ward, String district, String province) {
        List<String> address = new ArrayList<>();
        if (StringUtils.isNotEmpty(street)) {
            address.add(street);
        }
        if (StringUtils.isNotEmpty(ward)) {
            address.add(ward);
        }
        if (StringUtils.isNotEmpty(district)) {
            address.add(district);
        }
        if (StringUtils.isNotEmpty(province)) {
            address.add(province);
        }
        if (address.isEmpty()) {
            return null;
        }
        return String.join(", ", address);
    }

    public static String buildAddress(String street, String ward, String district, String province, String address) {

        String[] arrAddress = Strings.isEmpty(address) ? new String[0] : address.trim().split(Symbol.COMMA);
        List<String> dataAddress = new ArrayList<>();

        String[] addressFields = {street, ward, district, province};

        for (int i = 0; i < addressFields.length; i++) {
            if (StringUtils.isNotEmpty(addressFields[i])) {
                dataAddress.add(addressFields[i]);
            } else if (arrAddress.length > i) {
                dataAddress.add(arrAddress[i]);
            }
        }

        if (dataAddress.isEmpty()) {
            return null;
        }
        return String.join(Symbol.COMMA_SPACE, dataAddress);
    }

    public static String removeAccentAndSpecialCharacters(String s) {
        if (org.apache.commons.lang3.StringUtils.isEmpty(s))
            return "";

        String temp = Normalizer.normalize(s, Normalizer.Form.NFD);
        temp = temp.replaceAll("[^\\p{ASCII}]", "");
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        temp = pattern.matcher(temp).replaceAll("");
        temp = temp.replaceAll("[^a-zA-Z0-9 ]", "");
        temp = temp.replaceAll("đ", "d");

        // Loại bỏ các kí tự đặc biệt khỏi chuỗi
        temp = temp.replaceAll("[^a-zA-Z0-9 ]", "");

        return temp;
    }

    public static String removeSpecialCharacters(String s) {
        if (org.apache.commons.lang3.StringUtils.isEmpty(s))
            return "";
        return  s.replaceAll("[^a-zA-Z0-9]", "");
    }

    public static String redact(@NonNull String string) {
        try {
            for (String key : redactKeys) {
                Matcher matcher = Pattern.compile(String.format("\"%s\"%s", key, REGEX_FILTER_KEY)).matcher(string);
                if (matcher.find() && matcher.group(1) != null) {
                    String group = matcher.group(1);
                    if (!ObjectUtils.isEmpty(group.trim()) && !"\"\"".equals(group)) {
                        string = string.replace(group, "\"**********\"");
                    }
                }
            }
            return string;
        } catch (Exception e) {
            return string;
        }
    }

    public static List<String> approximateSearchInList(List<String> strings, String pattern) {

        if (CollectionUtils.isEmpty(strings)) {
            return new ArrayList<>();
        }
        if (StringUtils.isEmpty(pattern)) {
            return strings;
        }
        List<String> textSearch = new ArrayList<>();
        int maxErrors = pattern.length();
        for (int i = 0; i < strings.size(); i++) {
            String text = strings.get(i);

            if (isApproximateBruteForceSearchAll(text, pattern, maxErrors)) {
                textSearch.add(text);
            }
        }
        return textSearch;
    }

    public static boolean isApproximateBruteForceSearchAll(String text, String pattern, int maxErrors) {
        for (int i = 0; i <= text.length() - pattern.length(); i++) {
            String subtext = text.toLowerCase().substring(i);
            int errors = 0;

            for (int j = 0; j < pattern.length(); j++) {
                if (subtext.charAt(j) != pattern.charAt(j)) {
                    errors++;
                    if (errors > maxErrors) {
                        break;
                    }
                }
            }
            if (errors < maxErrors) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsVietnameseCharacters(String input) {
        String vietnamesePattern = ".*[\\p{InLatin_1_Supplement}&&[^a-zA-Z0-9]]+.*";
        return Pattern.matches(vietnamesePattern, input);
    }

    public static boolean containsSpecialCharacters(String input) {
        String specialCharactersPattern = ".*[^a-zA-Z0-9\\s]+.*";
        return Pattern.matches(specialCharactersPattern, input);
    }

    public static boolean isPositiveNonFloatingNumber(String input) {
        // Regular expression for positive non-floating numbers
        String pattern = "\\d+";

        if (input.matches(pattern)) {
            try {
                long longValue = Long.parseLong(input);
                return longValue > 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        return false;
    }

    public static <T> Map<String, T> convertListToMap(List<T> list, Function<T, String> keyExtractor) {
        return list.stream().collect(Collectors.toMap(keyExtractor, Function.identity(), (oldVal, newVal) -> newVal));
    }

    public static String convertDateToyyyyMMdd(Long input){
        String pattern = "yyyyMMdd";
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
        Date date = new Date(input);
        return simpleDateFormat.format(date);
    }

    public static List<String> getAllDuplicates(List<String> list) {
        Set<String> set = new HashSet<>();
        Set<String> duplicates = new HashSet<>();

        for (String item : list) {
            if (!set.add(item)) {
                duplicates.add(item);
            }
        }

        return new ArrayList<>(duplicates);
    }

    public static boolean isMatchRegex(String str, String regex) {
        try {
            if (str == null || regex == null) {
                return false;
            }

            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(str);

            return matcher.matches();
        } catch (Exception e) {
            log.error(e.getMessage());
            return false;
        }

    }


    public static String[] getBlankProperties(Object obj) {
        return Arrays.stream(obj.getClass().getDeclaredFields())
                .filter(f -> {
                    f.setAccessible(true);
                    try {
                        Object value = f.get(obj);
                        return value == null || (value instanceof String && StringUtils.isBlank((String) value));
                    } catch (IllegalAccessException e) {
                        return true;
                    }
                })
                .map(Field::getName)
                .toArray(String[]::new);
    }

    public static String[] getExistedProperties(Object obj) {
        return Arrays.stream(obj.getClass().getDeclaredFields())
                .filter(f -> {
                    f.setAccessible(true);
                    try {
                        Object value = f.get(obj);
                        if (value instanceof String) {
                            return !StringUtils.isBlank((String) value);
                        }
                        return value != null;
                    } catch (IllegalAccessException e) {
                        return true;
                    }
                })
                .map(Field::getName)
                .toArray(String[]::new);
    }
}
