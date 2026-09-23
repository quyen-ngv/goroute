package com.ds.goroute.utils;

import com.ds.goroute.config.filter.AcceptLanguageFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PassportTextResolverTest {

    @Test
    void vietnameseReaderGetsSourceText() throws Exception {
        assertThat(resolveUnder("vi-VN", "Hồ Gươm", "Hoan Kiem Lake")).isEqualTo("Hồ Gươm");
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "en-US", "ja", "ko", "de", "fr"})
    void everyOtherLanguageGetsEnglish(String header) throws Exception {
        assertThat(resolveUnder(header, "Hồ Gươm", "Hoan Kiem Lake")).isEqualTo("Hoan Kiem Lake");
    }

    @Test
    void missingHeaderGetsEnglish() throws Exception {
        assertThat(resolveUnder(null, "Hồ Gươm", "Hoan Kiem Lake")).isEqualTo("Hoan Kiem Lake");
    }

    @Test
    void blankEnglishFallsBackToSourceText() throws Exception {
        assertThat(resolveUnder("en", "Hồ Gươm", " ")).isEqualTo("Hồ Gươm");
        assertThat(resolveUnder("ja", "Hồ Gươm", null)).isEqualTo("Hồ Gươm");
    }

    private String resolveUnder(String acceptLanguage, String vietnamese, String english) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (acceptLanguage != null) {
            request.addHeader(AcceptLanguageFilter.HEADER_NAME, acceptLanguage);
        }
        AtomicReference<String> resolved = new AtomicReference<>();
        new AcceptLanguageFilter().doFilter(request, new MockHttpServletResponse(),
                (req, res) -> resolved.set(PassportTextResolver.resolve(vietnamese, english)));
        return resolved.get();
    }
}
