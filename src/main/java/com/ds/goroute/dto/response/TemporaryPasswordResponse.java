package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class TemporaryPasswordResponse {
    private UUID userId;
    private String temporaryPassword;
    private boolean mustChangePassword;
}
