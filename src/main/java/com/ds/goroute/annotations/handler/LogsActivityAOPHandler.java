package com.ds.goroute.annotations.handler;

import com.ds.goroute.annotations.LogsActivityAnnotation;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.utils.JsonUtils;
import com.ds.goroute.utils.Utils;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static net.logstash.logback.argument.StructuredArguments.entries;

@Aspect
@Component
@Order(value = 1)
@Slf4j
public class LogsActivityAOPHandler extends BaseService {

    private final HttpServletRequest httpServletRequest;

    public static final String service_name = "service_name";

    public static final String request_path = "request_path";

    public static final String query_string = "query_string";

    public static final String code_file = "code_file";

    public static final String method_name = "method_name";

    public static final String message_type = "message_type";

    public static final String request_id = "request_id";

    public static final String request = "request";

    public static final String response = "response";

    public static final String execution_time = "execution_time";

    public static final String status_code = "status_code";

    public static final String error_code = "error_code";

    public static final String headers = "headers";

    public LogsActivityAOPHandler(HttpServletRequest httpServletRequest) {
        this.httpServletRequest = httpServletRequest;
    }

    @Around("execution(* *(..)) && @annotation(logsActivityAnnotation)")
    @SneakyThrows
    public Object logsActivityAnnotation(ProceedingJoinPoint point, LogsActivityAnnotation logsActivityAnnotation) {
        // Parameter
        Object objectRequest = point.getArgs().length > 0 ? point.getArgs()[0] : httpServletRequest.getParameterMap();
        String requestId = getRequestId();
        // Log request
        Map<String, Object> mapCustomizeLog = new HashMap<>();
        mapCustomizeLog.put(LogsActivityAOPHandler.request_path, httpServletRequest.getRequestURI());
        mapCustomizeLog.put(LogsActivityAOPHandler.query_string, httpServletRequest.getQueryString());
        mapCustomizeLog.put(LogsActivityAOPHandler.code_file, point.getSignature().getDeclaringTypeName());
        mapCustomizeLog.put(LogsActivityAOPHandler.method_name, point.getSignature().getName());
        mapCustomizeLog.put(LogsActivityAOPHandler.message_type, LogsActivityAOPHandler.request);
        mapCustomizeLog.put(LogsActivityAOPHandler.request_id, requestId);
        mapCustomizeLog.put(LogsActivityAOPHandler.headers, Utils.redact(JsonUtils.convertObjectToString(getHeaders())));
        displayLog(objectRequest, mapCustomizeLog);

        // Process and get response
        long timeStart = new Date().getTime();
        Object objectResponse = point.proceed();

        // Log response
        long timeHandle = new Date().getTime() - timeStart;
        mapCustomizeLog.put(LogsActivityAOPHandler.execution_time, timeHandle);
        mapCustomizeLog.put(LogsActivityAOPHandler.code_file, point.getSignature().getDeclaringTypeName());
        mapCustomizeLog.put(LogsActivityAOPHandler.method_name, point.getSignature().getName());
        mapCustomizeLog.put(LogsActivityAOPHandler.request_id, requestId);
        mapCustomizeLog.put(LogsActivityAOPHandler.message_type, LogsActivityAOPHandler.response);
        mapCustomizeLog.put(LogsActivityAOPHandler.headers, Utils.redact(JsonUtils.convertObjectToString(getHeaders())));

        displayLog(objectResponse, mapCustomizeLog);
        return objectResponse;
    }

    private void displayLog(Object messageObject, Map<String, Object> mapCustomizeLog) throws JsonProcessingException {
        if (messageObject instanceof Exception) {
            Exception e = (Exception) messageObject;
            if (e instanceof MethodArgumentNotValidException) {
                log.info(Utils.redact(objectMapper.writeValueAsString(getRequestBody())), entries(mapCustomizeLog));
                return;
            } else if (e instanceof BusinessException) {
                BusinessException businessException = (BusinessException) e;
                if (!ObjectUtils.isEmpty(businessException.getError())) {
                    if (!ObjectUtils.isEmpty(businessException.getError().getMessage())) {
                        log.info(Utils.redact(e.getMessage()), entries(mapCustomizeLog));
                        return;
                    } else if (!ObjectUtils.isEmpty(businessException.getError().getData())) {
                        log.info(Utils.redact(objectMapper.writeValueAsString(businessException.getError().getData())),
                                entries(mapCustomizeLog));
                        return;
                    }
                }
            } else if (!ObjectUtils.isEmpty(e.getMessage())) {
                log.info(Utils.redact(e.getMessage()), entries(mapCustomizeLog));
            }

            if (mapCustomizeLog.get(LogsActivityAOPHandler.message_type).toString().equals(LogsActivityAOPHandler.request)) {
                log.error("Request_id: {}, Exception: ", getRequestId(), e);
            }
        } else {
            if (mapCustomizeLog.get(LogsActivityAOPHandler.message_type).toString().equals(LogsActivityAOPHandler.response)) {
                if (messageObject instanceof ResponseEntity) {
                    ResponseEntity responseEntity = (ResponseEntity) messageObject;
                    BaseResponse baseResponse = JsonUtils.getGenericObject(responseEntity.getBody(), BaseResponse.class);
                    log.info(Utils.redact(objectMapper.writeValueAsString(baseResponse)), entries(mapCustomizeLog));
                    return;
                }
            }

            log.info(Utils.redact(objectMapper.writeValueAsString(messageObject)), entries(mapCustomizeLog));
        }
    }
}