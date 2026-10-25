package com.ds.goroute.type;

/** Kinds of entry in {@code ai_credit_transactions}; the CHECK constraint in V254 lists the same. */
public enum AiCreditTransactionType {
    /** The plan bucket reset to the plan's allowance for a new period (debt is paid from it). */
    PLAN_REFILL,
    /** One model call, at its cost x AI_CREDIT.MARKUP. */
    CHARGE,
    /** A charge given back because its task failed on our side. */
    REFUND,
    /** Credits bought; reserved for the payment integration. */
    PURCHASE,
    /** An operator's correction from the admin console. */
    ADJUST
}
