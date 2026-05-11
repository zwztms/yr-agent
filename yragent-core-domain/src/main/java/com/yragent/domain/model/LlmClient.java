package com.yragent.domain.model;

public interface LlmClient {

    String chatCompletion(String prompt);

    String structuredCompletion(String prompt, String schema);
}
