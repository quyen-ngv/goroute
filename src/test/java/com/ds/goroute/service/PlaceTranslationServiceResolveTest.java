package com.ds.goroute.service;

import com.ds.goroute.entity.PlaceTranslation;
import com.ds.goroute.type.TranslationLocale;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceTranslationServiceResolveTest {

    @Test
    void chineseFallsBackToTheOtherScriptBeforeEnglish() {
        List<PlaceTranslation> rows = rows(TranslationLocale.VI, TranslationLocale.EN, TranslationLocale.ZH_TW);

        assertThat(PlaceTranslationService.resolveFrom(rows, "zh-CN").getLocale()).isEqualTo(TranslationLocale.ZH_TW);
        assertThat(PlaceTranslationService.resolveFrom(rows(TranslationLocale.EN, TranslationLocale.ZH_CN), "zh-HK")
                .getLocale()).isEqualTo(TranslationLocale.ZH_CN);
    }

    @Test
    void otherLanguagesFallBackToEnglishThenVietnamese() {
        assertThat(PlaceTranslationService.resolveFrom(rows(TranslationLocale.VI, TranslationLocale.EN), "fr")
                .getLocale()).isEqualTo(TranslationLocale.EN);
        assertThat(PlaceTranslationService.resolveFrom(rows(TranslationLocale.VI, TranslationLocale.ZH_TW), "ko")
                .getLocale()).isEqualTo(TranslationLocale.VI);
        assertThat(PlaceTranslationService.fallbackChain("zh-CN")).containsExactly(
                TranslationLocale.ZH_CN, TranslationLocale.ZH_TW, TranslationLocale.EN, TranslationLocale.VI);
        assertThat(PlaceTranslationService.fallbackChain("en")).containsExactly(TranslationLocale.EN, TranslationLocale.VI);
    }

    @Test
    void originalIsTheVietnameseRow() {
        assertThat(PlaceTranslationService.original(rows(TranslationLocale.EN, TranslationLocale.VI)).getName())
                .isEqualTo("name-vi");
        assertThat(PlaceTranslationService.original(rows(TranslationLocale.EN))).isNull();
    }

    private static List<PlaceTranslation> rows(TranslationLocale... locales) {
        return Arrays.stream(locales)
                .map(locale -> PlaceTranslation.builder().locale(locale).name("name-" + locale.code()).build())
                .toList();
    }
}
