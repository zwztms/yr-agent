package com.yragent.web.dto;

import java.util.List;

public record GateInputRequest(
        String understanding,
        String risk,
        List<String> confirmedCodes
) {}
