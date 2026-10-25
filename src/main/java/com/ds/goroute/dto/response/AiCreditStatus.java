package com.ds.goroute.dto.response;

import java.math.BigDecimal;

/**
 * Answer to a worker's call-log report: the balance after the charge, and whether the task must
 * stop now (balance below minus AI_CREDIT.NEGATIVE_FLOOR; never for beta users).
 */
public record AiCreditStatus(BigDecimal creditBalance, boolean creditExhausted) {
}
