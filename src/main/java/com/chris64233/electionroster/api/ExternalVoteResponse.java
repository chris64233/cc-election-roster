package com.chris64233.electionroster.api;

import java.time.Instant;

/** 其他渠道投票登记回执；outcome 说明本地待决选票/凭证的处置，均不含票面内容。 */
public record ExternalVoteResponse(
        Long electionId,
        String voterRef,
        String channel,
        String externalRef,
        String outcome,
        Instant votedAt) {
}
