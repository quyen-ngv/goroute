package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/** One step the worker reports while answering; shown live in the chat as Pengoo's "thinking". */
@Data
public class AssistantStepRequest {
    @NotBlank
    @Pattern(regexp = "[a-z_]{2,40}")
    private String key;
    @Size(max = 8)
    private Map<String, Object> params;
    @Size(max = 500)
    private String text;
}
