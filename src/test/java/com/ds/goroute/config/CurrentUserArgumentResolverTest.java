package com.ds.goroute.config;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.constant.RequestKeyConstant;
import com.ds.goroute.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserArgumentResolverTest {

    private final CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver();
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @SuppressWarnings("unused")
    static void endpoints(@CurrentUser UUID required,
                          @CurrentUser(required = false) UUID optional,
                          UUID unannotated) {
    }

    private MethodParameter parameter(int index) throws NoSuchMethodException {
        return new MethodParameter(CurrentUserArgumentResolverTest.class
                .getDeclaredMethod("endpoints", UUID.class, UUID.class, UUID.class), index);
    }

    private Object resolve(int index) throws Exception {
        return resolver.resolveArgument(parameter(index), null, new ServletWebRequest(request), null);
    }

    @Test
    void resolvesTheIdTheAuthenticationFiltersLeftBehind() throws Exception {
        UUID userId = UUID.randomUUID();
        request.setAttribute(RequestKeyConstant.USER_ID, userId);

        assertThat(resolve(0)).isEqualTo(userId);
    }

    /**
     * The point of the annotation: {@code @RequestAttribute("userId")} answered an
     * anonymous request with an unhandled binding exception, which surfaced as a 500.
     */
    @Test
    void anonymousRequestIsRejectedAsUnauthorizedRatherThanFailing() {
        assertThatThrownBy(() -> resolve(0))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getError().getHttpStatus())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void optionalParameterStaysNullForAnonymousCallers() throws Exception {
        assertThat(resolve(1)).isNull();
    }

    @Test
    void onlyClaimsAnnotatedUuidParameters() throws Exception {
        assertThat(resolver.supportsParameter(parameter(0))).isTrue();
        assertThat(resolver.supportsParameter(parameter(1))).isTrue();
        assertThat(resolver.supportsParameter(parameter(2))).isFalse();
    }
}
