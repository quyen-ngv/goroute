package com.ds.goroute.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@SuperBuilder
public class Meta {

    private Integer code;

    private Long pageIndex;

    private Long pageSize;

    private Long totalItems;

    private List<ErrorViolation> errors;

    private String message;

    private String requestId;


    public Meta(String requestId, int code, Long pageIndex, Long pageSize, long totalItems) {
        this.requestId = requestId;
        this.code = code;
        this.pageSize = pageSize;
        this.pageIndex = pageIndex;
        this.totalItems = totalItems;
    }


    public Meta(String requestId, int code, String message) {
        this.requestId = requestId;
        this.code = code;
        this.message = message;
    }
}