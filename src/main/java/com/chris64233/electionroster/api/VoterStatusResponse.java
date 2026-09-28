package com.chris64233.electionroster.api;

import java.time.Instant;

/**
 * 选民签发状态视图：只含签发/消费/补正/有效结果事实，不含票面选择。
 * cureStatus：邮寄票补正流程状态（NOT_REQUIRED/PENDING/CONFIRMED/FAILED），临时票为 null。
 * effectiveVoteSource：该选民名下唯一有效结果的来源；尚无有效结果时为 null。
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
        String cureStatus,
        String effectiveVoteSource) {
}
