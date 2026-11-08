package com.ds.goroute.type;

/** How money changed hands in a settlement. Informational only; it does not change a balance. */
public enum SettlementMethod {
    CASH,
    BANK_TRANSFER,
    E_WALLET,
    OTHER
}
