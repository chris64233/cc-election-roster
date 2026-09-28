package com.chris64233.electionroster.api;

import java.time.Instant;

/**
 * 补正确认结果。restored=true 表示原暂存选票已恢复计入；
 * 失败情形（其他渠道投票/逾期/资格不符/选区不符）以 409 返回并附带终态原因。
 */
public record CureConfirmationResponse(
        Long issuanceId,
        String cureStatus,
        String reason,
        Instant decidedAt,
        boolean restored) {
}
