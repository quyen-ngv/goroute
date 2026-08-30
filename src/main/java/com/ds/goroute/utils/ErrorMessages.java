package com.ds.goroute.utils;

import com.ds.goroute.exception.BusinessError;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.util.ObjectUtils;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Looks an error code up in the {@code i18n/errors} bundle for the caller's locale.
 *
 * <p>Both the controller base class and the exception handler need this, and neither
 * should have to inherit a service to get at it.
 */
public final class ErrorMessages {

    private static final String BUNDLE = "i18n/errors";

    private ErrorMessages() {
    }

    /**
     * @return the localized text, or {@code key} itself when the bundle has no entry for
     *         it — an untranslated code is more useful to a caller than an empty message
     */
    public static String of(String key) {
        try {
            return ResourceBundle.getBundle(BUNDLE, LocaleContextHolder.getLocale()).getString(key);
        } catch (MissingResourceException exception) {
            return key;
        }
    }

    public static String of(int code) {
        return of(String.valueOf(code));
    }

    /**
     * @return the error's own message when it carries one, otherwise the localized text
     *         for its code
     */
    public static String of(BusinessError error) {
        return ObjectUtils.isEmpty(error.getMessage()) ? of(error.getCode()) : error.getMessage();
    }
}
