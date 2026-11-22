package com.ds.goroute.type;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationLocaleTest {

    @ParameterizedTest
    @CsvSource({
            "vi, VI", "vi-VN, VI", "EN-us, EN", "en_GB, EN",
            "zh, ZH_CN", "zh-CN, ZH_CN", "zh_cn, ZH_CN", "zh-Hans, ZH_CN", "zh-Hans-CN, ZH_CN", "zh-SG, ZH_CN",
            "zh-TW, ZH_TW", "zh-Hant, ZH_TW", "zh-Hant-TW, ZH_TW", "zh-HK, ZH_TW", "zh-MO, ZH_TW",
            "pt, PT", "pt-BR, PT", "pt-PT, PT",
            "fr, FR", "fr-CA, FR", "it-IT, IT", "de-AT, DE", "es-419, ES", "ja-JP, JA", "ko-KR, KO",
            "ZH_TW, ZH_TW",
            "ms, MS", "ms-MY, MS", "id, ID", "id-ID, ID", "lo, LO", "km, KM", "fil, FIL", "fil-PH, FIL",
            "ar, AR", "ar-EG, AR"
    })
    void normalisesLanguageTags(String tag, TranslationLocale expected) {
        assertThat(TranslationLocale.fromCode(tag)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "xx", "nl", "nl-BE", "sw-KE", "*"})
    void unknownLanguagesAreEmpty(String tag) {
        assertThat(TranslationLocale.fromCode(tag)).isEmpty();
    }

    @Test
    void nullIsEmpty() {
        assertThat(TranslationLocale.fromCode(null)).isEmpty();
    }

    @Test
    void onlyTheChineseScriptsHaveASibling() {
        assertThat(TranslationLocale.ZH_CN.sibling()).contains(TranslationLocale.ZH_TW);
        assertThat(TranslationLocale.ZH_TW.sibling()).contains(TranslationLocale.ZH_CN);
        assertThat(TranslationLocale.JA.sibling()).isEmpty();
    }

    @Test
    void everyCodeReadsBackAsItsOwnLocale() {
        for (TranslationLocale locale : TranslationLocale.values()) {
            assertThat(TranslationLocale.fromCode(locale.code())).contains(locale);
        }
    }
}
