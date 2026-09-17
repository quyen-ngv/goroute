package com.ds.goroute.dto;

import lombok.Data;

import java.util.UUID;

/**
 * How many splits one expense carries.
 *
 * <p>Projection used to render a whole trip's public expense list without one count query
 * per expense.
 */
@Data
public class ExpenseSplitCount {
    private UUID expenseId;
    private Integer splitCount;
}
