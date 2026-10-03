package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** The open splits of one person that a single reminder covers. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WalletReminderRequest {
    @NotEmpty
    @Size(max = 200)
    private List<@NotNull UUID> splitIds;
}
