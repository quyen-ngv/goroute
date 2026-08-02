package com.ds.goroute.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LocationDescriptionType {
    DESCRIPTION(""),
    VIBE("Vibe ở đây thế nào"),
    ACCOMODATION("Ngủ lại ở đâu"),
    CUISINE("Đặc sản tại đây"),
    SEASON("Nên đi mùa nào"),
    NOTES("Lưu ý thêm");

    private final String defaultTitle;
}
