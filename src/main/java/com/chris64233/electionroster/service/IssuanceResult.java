package com.chris64233.electionroster.service;

import com.chris64233.electionroster.domain.BallotIssuance;
import com.chris64233.electionroster.domain.IssuanceType;

import java.time.Instant;

/** 签发结果视图。replayed=true 表示本次请求命中幂等重放，返回的是原签发结果。 */
public record IssuanceResult(
        Long issuanceId,
        String credentialToken,
        IssuanceType type,
        String status,
        String eventId,
        String voterRef,
        String precinctCode,
        String ballotStyleCode,
        String pollingPlace,
        Instant issuedAt,
        boolean replayed) {

    static IssuanceResult of(BallotIssuance issuance, boolean replayed) {
        return new IssuanceResult(
                issuance.getId(),
                issuance.getCredentialToken(),
                issuance.getType(),
                issuance.getStatus().name(),
                issuance.getEventId(),
                issuance.getVoter().getVoterRef(),
                issuance.getPrecinct().getCode(),
                issuance.getBallotStyle().getCode(),
                issuance.getPollingPlace(),
                issuance.getCreatedAt(),
                replayed);
    }
}
