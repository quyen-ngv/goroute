package com.ds.goroute.partneronboarding.domain;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * One step's answers, read by name and type.
 *
 * <p>Every accessor tolerates absence: the client owns step validation, so a half-finished
 * draft must still be readable rather than blow up mid-submit. The {@code required*}
 * accessors are the exception and exist for the handful of values a listing cannot be
 * created without — they fail with the step and field named, which is what lets the client
 * jump the user back to the right screen.
 */
public final class StepData {

    private static final StepData EMPTY = new StepData("", Collections.emptyMap());

    private final String stepCode;
    private final Map<String, Object> fields;

    private StepData(String stepCode, Map<String, Object> fields) {
        this.stepCode = stepCode;
        this.fields = fields;
    }

    @SuppressWarnings("unchecked")
    static StepData of(String stepCode, Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return stepCode == null || stepCode.isEmpty() ? EMPTY : new StepData(stepCode, Collections.emptyMap());
        }
        return new StepData(stepCode, (Map<String, Object>) map);
    }

    public boolean isEmpty() {
        return fields.isEmpty();
    }

    public boolean has(String field) {
        Object value = fields.get(field);
        return value != null && !(value instanceof String text && text.isBlank());
    }

    // --- scalars ------------------------------------------------------------------------

    public String text(String field) {
        Object value = fields.get(field);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    public String text(String field, String fallback) {
        String value = text(field);
        return value == null ? fallback : value;
    }

    public String requiredText(String field) {
        String value = text(field);
        if (value == null) {
            throw missing(field);
        }
        return value;
    }

    public Integer integer(String field) {
        BigDecimal value = decimal(field);
        return value == null ? null : value.intValue();
    }

    public int integer(String field, int fallback) {
        Integer value = integer(field);
        return value == null ? fallback : value;
    }

    public int requiredInteger(String field) {
        Integer value = integer(field);
        if (value == null) {
            throw missing(field);
        }
        return value;
    }

    public BigDecimal decimal(String field) {
        Object value = fields.get(field);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        try {
            String text = value.toString().trim();
            return text.isEmpty() ? null : new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public BigDecimal requiredDecimal(String field) {
        BigDecimal value = decimal(field);
        if (value == null) {
            throw missing(field);
        }
        return value;
    }

    public boolean bool(String field, boolean fallback) {
        Object value = fields.get(field);
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(value.toString().trim());
    }

    /** {@code HH:mm} as written by both clients' time pickers; unparseable reads as absent. */
    public LocalTime time(String field) {
        String value = text(field);
        if (value == null) {
            return null;
        }
        try {
            return LocalTime.parse(value.length() > 5 ? value.substring(0, 5) : value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    // --- collections --------------------------------------------------------------------

    public List<String> strings(String field) {
        List<String> result = new ArrayList<>();
        for (Object item : rawList(field)) {
            if (item == null) {
                continue;
            }
            String text = item.toString().trim();
            if (!text.isEmpty()) {
                result.add(text);
            }
        }
        return result;
    }

    /** Nested objects inside a step, each readable with the same accessors. */
    public List<StepData> objects(String field) {
        List<StepData> result = new ArrayList<>();
        for (Object item : rawList(field)) {
            StepData nested = StepData.of(stepCode + "." + field, item);
            if (!nested.isEmpty()) {
                result.add(nested);
            }
        }
        return result;
    }

    public StepData object(String field) {
        return StepData.of(stepCode + "." + field, fields.get(field));
    }

    private List<?> rawList(String field) {
        Object value = fields.get(field);
        return value instanceof List<?> list ? list : Collections.emptyList();
    }

    private BusinessException missing(String field) {
        return new BusinessException(ErrorConstant.ONBOARDING_INCOMPLETE,
                "Step '" + stepCode + "' is missing '" + field + "'");
    }
}
