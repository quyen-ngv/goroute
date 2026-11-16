package com.ds.goroute.config.filter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AcceptLanguageFilterTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "vi-VN,vi;q=0.9,en;q=0.8 | vi",
            "en-US | en",
            "zh-CN | zh-CN",
            "zh | zh-CN",
            "zh-Hans-CN;q=0.9 | zh-CN",
            "zh-SG | zh-CN",
            "zh-TW | zh-TW",
            "zh-Hant | zh-TW",
            "zh-HK | zh-TW",
            "zh_MO | zh-TW",
            "pt-BR | pt",
            "fr-CA | fr",
            "it | it",
            "de-DE | de",
            "es-419 | es",
            "ko | ko",
            "th-TH | th",
            "ja | ja",
            "ru | ru",
            "hi-IN | hi",
            "nl-NL | en",
            "ar | en",
            "* | en"
    })
    void normalisesTheFirstTag(String header, String expected) {
        assertThat(AcceptLanguageFilter.normalize(header)).isEqualTo(expected);
    }

    @Test
    void missingHeaderIsEnglish() {
        assertThat(AcceptLanguageFilter.normalize(null)).isEqualTo("en");
        assertThat(AcceptLanguageFilter.normalize(" ")).isEqualTo("en");
    }

    @Test
    void simplifiedChineseRequestRunsWithAChineseLocale() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AcceptLanguageFilter.HEADER_NAME, "zh-Hans-CN");
        AtomicReference<String> code = new AtomicReference<>();
        AtomicReference<Locale> locale = new AtomicReference<>();
        new AcceptLanguageFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            code.set(AcceptLanguageFilter.currentCode());
            locale.set(AcceptLanguageFilter.currentLocale());
        });
        assertThat(code.get()).isEqualTo("zh-CN");
        assertThat(locale.get()).isEqualTo(Locale.SIMPLIFIED_CHINESE);
    }
}
