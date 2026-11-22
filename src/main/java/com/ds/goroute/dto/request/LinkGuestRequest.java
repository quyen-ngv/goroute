package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkGuestRequest {
    @NotNull(message = "Target user ID is required")
    private UUID targetUserId; // ID của user sẽ được link với guest

    // The caller has shown the user that expenses this guest paid will name the target user
    // as payer. Without it, a guest who paid anything is refused with TRIP_GUEST_HAS_EXPENSES.
    private Boolean confirmMoveExpenses;
}
