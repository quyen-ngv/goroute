package com.ds.goroute.exception;

import com.ds.goroute.constant.ErrorConstant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class CommonExceptionHandlerTest {

    private final CommonExceptionHandler handler =
            new CommonExceptionHandler(new MockHttpServletRequest());

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
    void answersUnknownRoutesWithNotFound() {
        var response = handler.handleNoResource(
                new NoResourceFoundException(HttpMethod.GET, "v1/api/quests/x/pack"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMeta().getMessage()).isEqualTo("Not found");
    }

    @Test
    void answersWrongMethodWithMethodNotAllowed() {
        var response = handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("DELETE"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void sanitizesUnexpectedFailures() {
        var response = handler.handleUnexpectedException(new RuntimeException("database password leaked"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMeta().getMessage()).isEqualTo("Internal server error");
    }
}
