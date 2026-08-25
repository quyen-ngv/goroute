package com.ds.goroute.config;

import com.ds.goroute.type.PlaceReviewRefreshRerunMode;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class PlaceReviewRefreshRerunModeConverter implements Converter<String, PlaceReviewRefreshRerunMode> {
    @Override
    public PlaceReviewRefreshRerunMode convert(String source) {
        return PlaceReviewRefreshRerunMode.valueOf(source.trim().toUpperCase(Locale.ROOT));
    }
}
