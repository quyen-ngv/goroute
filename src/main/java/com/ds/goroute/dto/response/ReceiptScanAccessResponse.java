package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Whether the scan button shows. What a scan costs is in GET /me/ai-credits. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptScanAccessResponse {
    private boolean available;
}
