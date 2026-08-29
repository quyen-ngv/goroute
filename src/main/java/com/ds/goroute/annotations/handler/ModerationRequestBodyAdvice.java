package com.ds.goroute.annotations.handler;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.TextModerationService;
import com.ds.goroute.service.moderation.ModeratedFieldScanner;
import com.ds.goroute.service.moderation.ModerationVerdict;
import com.ds.goroute.service.moderation.PendingModerationFlags;
import com.ds.goroute.type.ModerationAction;
import com.ds.goroute.type.ModerationVisibility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;

import java.lang.reflect.Type;
import java.util.UUID;

/**
 * The single choke point for the shared text filter (MOD-03).
 *
 * <p>Every request body is walked for {@code @ModeratedText} fields before a controller
 * ever sees it. A BLOCK verdict stops the request here, so no service can accidentally
 * persist blocked content, and it stops it the same way for create and for update -- an
 * edit is not a way around the filter.
 *
 * <p>A FLAG verdict does not stop anything: the content is published and parked for the
 * human queue by {@code ModerationResponseAdvice} once it has an id.
 */
@ControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class ModerationRequestBodyAdvice extends BaseService implements RequestBodyAdvice {

    private static final String USER_ID_ATTRIBUTE = "userId";

    private final TextModerationService textModerationService;

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter,
                                           Type targetType,
                                           Class<? extends HttpMessageConverter<?>> converterType) {
        return inputMessage;
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        UUID userId = currentUserId();
        ModeratedFieldScanner.walk(body, (annotation, label, value) -> {
            ModerationVerdict verdict = textModerationService.evaluate(
                    annotation.contentType(), label, annotation.visibility(), value, userId);
            if (verdict.action() == ModerationAction.BLOCK) {
                throw blocked(verdict);
            }
            // Flagged text needs the human queue; public text needs the AI second pass.
            // Both only become actionable once the saved content has an id, so they are
            // parked here and picked up by ModerationResponseAdvice.
            if (verdict.action() == ModerationAction.FLAG
                    || annotation.visibility() == ModerationVisibility.PUBLIC) {
                PendingModerationFlags.add(new PendingModerationFlags.Pending(
                        annotation.contentType(), annotation.visibility(), label, verdict, value));
            }
        });
        return body;
    }

    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                  Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    /**
     * The message names the violated group so the app can tell the author what to change.
     * The draft itself is never touched -- the request simply does not take effect.
     */
    private BusinessException blocked(ModerationVerdict verdict) {
        String message = verdict.category() == null
                ? getMessage(ErrorConstant.CONTENT_BLOCKED_BY_MODERATION)
                : getMessage("moderation.category." + verdict.category().name());
        return new BusinessException(ErrorConstant.CONTENT_BLOCKED_BY_MODERATION, message);
    }

    private UUID currentUserId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        Object value = attributes.getAttribute(USER_ID_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        return value instanceof UUID userId ? userId : null;
    }
}
