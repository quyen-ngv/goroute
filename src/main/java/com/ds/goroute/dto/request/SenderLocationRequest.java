package com.ds.goroute.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Where the sender's device is as a message is sent, when the app already knows it.
 *
 * <p>Read only for a question to Pengoo in the 1:1 assistant chat ("near me"); every other
 * message ignores it. Never stored with the message.
 */
@Getter
@Setter
public class SenderLocationRequest {

    @NotNull
    @DecimalMin("-90")
    @DecimalMax("90")
    private BigDecimal latitude;

    @NotNull
    @DecimalMin("-180")
    @DecimalMax("180")
    private BigDecimal longitude;

    /** Radius of the fix in metres, when the device reports one. */
    @DecimalMin("0")
    @DecimalMax("1000000")
    private BigDecimal accuracyMeters;

    /** A position is personal data: it never prints, whatever logs the request. */
    @Override
    public String toString() {
        return "SenderLocationRequest[hidden]";
    }
}
