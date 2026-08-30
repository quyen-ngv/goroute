package com.ds.goroute.annotations.handler;

import com.ds.goroute.constant.RequestKeyConstant;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.service.AiTextModerationService;
import com.ds.goroute.service.ContentModerationService;
import com.ds.goroute.service.moderation.PendingModerationFlags;
import com.ds.goroute.type.ModerationAction;
import com.ds.goroute.type.ModerationFlagSource;
import com.ds.goroute.type.ModerationVisibility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

/**
 * Second half of the shared filter: links what the request-side advice parked to the
 * content that was actually saved.
 *
 * <p>A flag is about a specific piece of content, and that content only gets an id once
 * the service has stored it. Reading the id off the response instead of asking every
 * service to report it keeps the "you cannot forget to call the filter" property of
 * MOD-03 intact all the way to the review queue.
 *
 * <p>The AI layer (MOD-03, layer 2) is dispatched from here as well, after the content is
 * safely stored, so a slow or failing model never blocks a user.
 */
@ControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class ModerationResponseAdvice implements ResponseBodyAdvice<Object> {


    private final ContentModerationService contentModerationService;
    private final AiTextModerationService aiTextModerationService;

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        List<PendingModerationFlags.Pending> pending = PendingModerationFlags.drain();
        if (pending.isEmpty()) {
            return body;
        }
        UUID contentId = resolveContentId(body);
        if (contentId == null) {
            // Nothing to attach the flag to. Recorded rather than swallowed: it means an
            // endpoint returns a payload without an id and needs a look.
            log.warn("Moderation flags could not be linked: response has no resolvable id ({})",
                    returnType.getContainingClass().getSimpleName());
            return body;
        }
        UUID ownerId = currentUserId(request);
        for (PendingModerationFlags.Pending item : pending) {
            if (item.verdict().action() == ModerationAction.FLAG) {
                contentModerationService.flag(item.contentType(), contentId, ownerId,
                        ModerationFlagSource.TEXT_FILTER, item.verdict(), snapshot(item));
            }
            if (item.visibility() == ModerationVisibility.PUBLIC) {
                aiTextModerationService.reviewLater(item.contentType(), contentId, ownerId,
                        item.fieldLabel(), item.text());
            }
        }
        return body;
    }

    /**
     * Digs the created or updated entity id out of the standard response envelope. Every
     * mutation endpoint answers with the affected entity, so this covers create and
     * update alike.
     */
    private UUID resolveContentId(Object body) {
        Object payload = body instanceof BaseResponse<?> response ? response.getData() : body;
        if (payload == null) {
            return null;
        }
        try {
            Method getId = payload.getClass().getMethod("getId");
            Object id = getId.invoke(payload);
            return id instanceof UUID uuid ? uuid : null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private UUID currentUserId(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            Object value = servletRequest.getServletRequest().getAttribute(RequestKeyConstant.USER_ID);
            return value instanceof UUID userId ? userId : null;
        }
        return null;
    }

    private String snapshot(PendingModerationFlags.Pending item) {
        return com.ds.goroute.utils.JsonUtils.toJson(java.util.Map.of(
                "field", item.fieldLabel(),
                "visibility", item.visibility().name(),
                "text", item.text()));
    }
}
