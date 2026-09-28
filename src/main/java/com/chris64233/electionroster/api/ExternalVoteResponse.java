package com.chris64233.electionroster.api;

import java.time.Instant;

/** 其他渠道有效投票登记结果；只含登记事实，不含票面选择。 */
public record ExternalVoteResponse(
        String voterRef,
        String source,
        String channelRef,
        Instant registeredAt) {
}
