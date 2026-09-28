package com.chris64233.electionroster.api;

import java.time.Instant;

/** 补正材料提交回执：只回材料版本与哈希，不含票面信息。 */
public record CureMaterialResponse(
        Long issuanceId,
        int version,
        String materialHash,
        String cureStatus,
        Instant cureDeadline,
        Instant submittedAt) {
}
