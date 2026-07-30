package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @Size(min = 10, max = 100) private String temporaryPassword;
}
