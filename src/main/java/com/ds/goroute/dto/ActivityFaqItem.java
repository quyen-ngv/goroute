package com.ds.goroute.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ActivityFaqItem {
    @NotBlank @Size(max = 500)
    private String question;

    @NotBlank @Size(max = 5000)
    private String answer;
}
