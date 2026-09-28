package com.chris64233.electionroster.api;

import java.time.Instant;

/**
 * 补正处理结果。
 * outcome：
 * - SUBMITTED：新材料已登记，等待确认；
 * - CONFIRMED：补正确认，原选票恢复有效；
 * - FAILED：补正失败（截止/资格/选区/已通过其他渠道投票），原选票作废。
 * 不包含任何票面选择或内容。
 */
public record CureResponse(
        Long issuanceId,
        Integer version,
        String cureStatus,
        String outcome,
        String reason,
        Instant processedAt) {
}
