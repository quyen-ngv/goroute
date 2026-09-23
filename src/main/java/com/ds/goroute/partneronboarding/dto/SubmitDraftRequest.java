package com.ds.goroute.partneronboarding.dto;

import lombok.Data;

/** Optional body on submit; carries only the version the client last read. */
@Data
public class SubmitDraftRequest {

    private Long expectedVersion;
}
