package com.chris64233.electionroster.api;

import java.time.Instant;

public record IssuanceResponse(
        Long issuanceId,
        String eventNo,
        String credentialToken,
        String type,
        String status,
        String districtCode,
        String ballotStyleCode,
        String pollingPlace,
        Instant createdAt) {
}
