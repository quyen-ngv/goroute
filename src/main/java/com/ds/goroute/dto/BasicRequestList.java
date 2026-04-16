package com.ds.goroute.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@NoArgsConstructor
@AllArgsConstructor
@Data
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class BasicRequestList extends BasicRequest {

    private static final long serialVersionUID = -2411435498386121564L;

    @NotNull(message = "page_index must not be null!")
    private Long pageIndex;

    private Long pageStart;

    @NotNull(message = "page_size must not be null!")
    private Long pageSize;

    private Long totalItems;

    public BasicRequestList(Long totalItems, String requestId) {
        this.setTotalItems(totalItems);
        this.setRequestId(requestId);
    }

}