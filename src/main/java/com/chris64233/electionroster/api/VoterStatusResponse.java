package com.chris64233.electionroster.api;

import java.time.Instant;

/**
 * 选民签发状态视图：只含签发/消费/补正状态事实，不含票面选择，也不暴露补正材料明细。
 */
public record VoterStatusResponse(
        String voterRef,
        String voterStatus,
        boolean issued,
        String issuanceType,
        String issuanceStatus,
        String districtCode,
        String pollingPlace,
        Instant issuedAt,
        String adjudication,
        String cureStatus) {
}
