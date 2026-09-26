package com.chris64233.electionroster.api;

import java.time.Instant;

/** 选民签发状态视图：只含签发/消费事实，不含票面选择。 */
public record VoterStatusResponse(
        String voterRef,
        String voterStatus,
        boolean issued,
        String issuanceType,
        String issuanceStatus,
        String districtCode,
        String pollingPlace,
        Instant issuedAt,
        String adjudication) {
}
