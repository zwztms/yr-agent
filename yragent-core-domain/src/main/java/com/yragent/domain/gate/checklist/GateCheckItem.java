package com.yragent.domain.gate.checklist;

import java.util.List;

public record GateCheckItem(
        String id,
        String dimension,
        String question,
        List<String> keywords
) {}
