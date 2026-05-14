package com.yragent.domain.model;

import java.util.List;
import java.util.Map;

public interface LlmClient {

    String chatCompletion(String prompt);

    String chatCompletion(List<Map<String, String>> messages);

    String structuredCompletion(String prompt, String schema);

    String structuredCompletion(List<Map<String, String>> messages, String schema);
}
