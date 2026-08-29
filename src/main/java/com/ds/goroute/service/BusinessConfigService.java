package com.ds.goroute.service;

import com.ds.goroute.type.BusinessConfigKey;

/**
 * Reads runtime configuration owned by {@link BusinessConfigKey}. An invalid or missing
 * administrator value always resolves to the code default, so a bad edit degrades the
 * knob rather than the feature.
 */
public interface BusinessConfigService {
    int getInt(BusinessConfigKey configKey);

    double getDecimal(BusinessConfigKey configKey);

    boolean getBoolean(BusinessConfigKey configKey);

    String getText(BusinessConfigKey configKey);

    <E extends Enum<E>> E getEnum(BusinessConfigKey configKey, Class<E> enumType);
}
