package com.ds.goroute.exception;

import com.ds.goroute.constant.ErrorConstant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class CommonExceptionHandlerTest {

    private final CommonExceptionHandler handler = new CommonExceptionHandler();

    @BeforeEach
    void setUpRequest() {
        ReflectionTestUtils.setField(handler, "httpServletRequest", new MockHttpServletRequest());
        ReflectionTestUtils.setField(handler, "httpServletResponse", new MockHttpServletResponse());
    }

    @Test
    void mapsBusinessCodePrefixToHttpStatus() {
        assertThat(handler.handleBusinessException(
                        new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Invalid input"))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.handleBusinessException(
                        new BusinessException(ErrorConstant.NOT_FOUND, "Missing"))
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(handler.handleBusinessException(
                        new BusinessException(ErrorConstant.ALREADY_PROCESSED, "Conflict"))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void sanitizesUnexpectedFailures() {
        var response = handler.handleUnexpectedException(new RuntimeException("database password leaked"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMeta().getMessage()).isEqualTo("Internal server error");
    }
}
