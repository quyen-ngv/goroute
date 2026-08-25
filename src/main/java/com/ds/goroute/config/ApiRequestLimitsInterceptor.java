package com.ds.goroute.config;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class ApiRequestLimitsInterceptor implements HandlerInterceptor {

    private final ApiRequestLimitsProperties limits;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        validate(request, "page", 0, limits.getMaxPageIndex());
        validate(request, "size", 1, limits.getMaxPageSize());
        validate(request, "limit", 1, limits.getMaxResultLimit());
        return true;
    }

    private void validate(HttpServletRequest request, String parameter, long minimum, long maximum) {
        String[] values = request.getParameterValues(parameter);
        if (values == null) {
            return;
        }
        for (String value : values) {
            long parsed;
            try {
                parsed = Long.parseLong(value);
            } catch (NumberFormatException exception) {
                throw invalid(parameter, minimum, maximum);
            }
            if (parsed < minimum || parsed > maximum) {
                throw invalid(parameter, minimum, maximum);
            }
        }
    }

    private BusinessException invalid(String parameter, long minimum, long maximum) {
        return new BusinessException(
                ErrorConstant.INVALID_PARAMETERS,
                parameter + " must be between " + minimum + " and " + maximum,
                HttpStatus.BAD_REQUEST);
    }
}
