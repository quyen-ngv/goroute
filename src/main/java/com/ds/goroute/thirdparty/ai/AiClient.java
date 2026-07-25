package com.ds.goroute.thirdparty.ai;

import java.util.Optional;

/**
 * Provider-independent contract for AI text completions that must return JSON.
 */
public interface AiClient {

    Optional<String> completeJson(String systemPrompt, String userPrompt);
}
