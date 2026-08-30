package com.ds.goroute.config;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.constant.RequestKeyConstant;
import com.ds.goroute.exception.BusinessException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

/**
 * Resolves {@link CurrentUser} parameters from the request attribute the authentication
 * filters populate.
 *
 * <p>The single place that knows the attribute name, and the single place that decides
 * what an anonymous request means for a parameter that needs a caller.
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && UUID.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        Object value = webRequest.getAttribute(
                RequestKeyConstant.USER_ID, RequestAttributes.SCOPE_REQUEST);
        if (value instanceof UUID userId) {
            return userId;
        }
        CurrentUser annotation = parameter.getParameterAnnotation(CurrentUser.class);
        if (annotation != null && !annotation.required()) {
            return null;
        }
        throw new BusinessException(ErrorConstant.UNAUTHORIZED,
                "Authentication is required", HttpStatus.UNAUTHORIZED);
    }
}
