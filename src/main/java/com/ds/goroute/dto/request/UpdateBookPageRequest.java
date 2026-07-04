package com.ds.goroute.dto.request;

import com.ds.goroute.dto.response.BookSlotResponse;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBookPageRequest {
    @NotNull
    private List<BookSlotResponse> slots;
}
