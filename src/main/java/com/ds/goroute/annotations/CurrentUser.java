package com.ds.goroute.annotations;

import com.ds.goroute.annotations.CurrentUser;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds the authenticated caller's id to a controller parameter.
 *
 * <p>Replaces {@code @CurrentUser}, which repeated the attribute name at
 * every endpoint and — when the attribute was absent because the request was never
 * authenticated — failed with an unhandled {@code ServletRequestBindingException},
 * surfacing as a 500 instead of a 401.
 *
 * <p>Set {@code required = false} on endpoints that are legitimately reachable
 * anonymously and only personalise their answer when a caller is known; the parameter is
 * then {@code null} for anonymous callers.
 *
 * @see com.ds.goroute.config.CurrentUserArgumentResolver
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {

    /**
     * When {@code true} (the default) an anonymous request is rejected with 401 before
     * the controller method runs.
     */
    boolean required() default true;
}
