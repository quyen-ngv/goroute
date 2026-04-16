package com.ds.goroute.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JoinInQueueResponseDTO {

    private Boolean alreadyInQueue;

    private Long sequence;

    private Long timestamp;

}
