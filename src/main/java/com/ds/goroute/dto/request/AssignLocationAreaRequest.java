package com.ds.goroute.dto.request;

import lombok.Data;

import java.util.UUID;

/** Operator's choice of tourist area for one row. Null clears the row's area. */
@Data
public class AssignLocationAreaRequest {
    private UUID locationImageId;
}
