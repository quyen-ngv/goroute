package com.ds.goroute.dto;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.HotelFeeUnit;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** One fee a property lists, e.g. city tax (paid at the property) or a breakfast / crib charge. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotelFee {
    @ModeratedText(contentType = ModeratedContentType.PARTNER_LISTING, visibility = ModerationVisibility.PUBLIC)
    @NotBlank @Size(max = 200)
    private String name;

    /** Null when the amount varies; {@link #note} then explains it. */
    @DecimalMin("0")
    private BigDecimal amount;

    @Pattern(regexp = "[A-Z]{3}")
    private String currency;

    private HotelFeeUnit unit;

    @ModeratedText(contentType = ModeratedContentType.PARTNER_LISTING, visibility = ModerationVisibility.PUBLIC)
    @Size(max = 1000)
    private String note;
}
