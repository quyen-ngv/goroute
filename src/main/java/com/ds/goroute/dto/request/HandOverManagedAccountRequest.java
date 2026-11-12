package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Giving a managed account to its owner (POST /v1/api/admin/managed-accounts/{userId}/handover).
 * Without a password the owner signs in with Google on this email, which links to the account.
 */
@Data
public class HandOverManagedAccountRequest {

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    /** Optional; BCrypt refuses more than 72 bytes. */
    @Size(min = 10, max = 72)
    private String password;
}
