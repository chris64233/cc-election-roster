package com.chris64233.electionroster.api;

import java.time.Instant;

public record AdjudicationResponse(
        Long issuanceId,
        String adjudication,
        String reason,
        Instant decidedAt) {
}
