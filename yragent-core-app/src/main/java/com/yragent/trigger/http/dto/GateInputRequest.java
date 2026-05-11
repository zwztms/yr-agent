package com.yragent.trigger.http.dto;

import java.util.List;

public record GateInputRequest(
        String understanding,
        String risk,
        List<String> confirmedCodes
) {}
