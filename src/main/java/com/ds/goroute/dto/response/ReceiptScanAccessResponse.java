package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Whether the scan button shows, and how many scans are left today. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptScanAccessResponse {
    private boolean available;
    private int dailyLimit;
    private int usedToday;
}
