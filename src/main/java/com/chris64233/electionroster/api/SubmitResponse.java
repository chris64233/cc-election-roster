package com.chris64233.electionroster.api;

import java.time.Instant;

public record SubmitResponse(
        String receiptId,
        String contentHash,
        boolean counted,
        boolean duplicate,
        boolean held,
        Instant submittedAt) {
}
