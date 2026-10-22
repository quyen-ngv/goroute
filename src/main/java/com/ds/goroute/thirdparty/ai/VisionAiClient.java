package com.ds.goroute.thirdparty.ai;

import java.util.Optional;

/**
 * One image plus instructions in, one JSON object out (the receipt scan). Chosen by
 * {@code goroute.receipt-scan.provider} (RECEIPT_SCAN_PROVIDER, else AI_PROVIDER): {@code claude}
 * picks Claude, anything else OpenAI.
 */
public interface VisionAiClient {

    boolean isConfigured();

    /** Empty when the key is missing, the call fails, or the model returns nothing. */
    Optional<String> completeJson(String systemPrompt, String userPrompt, byte[] image, String mimeType);
}
