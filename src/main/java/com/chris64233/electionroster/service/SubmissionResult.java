package com.chris64233.electionroster.service;

/** 提交结果视图。 */
public record SubmissionResult(
        String outcome,
        Long castBallotId,
        Long provisionalBallotId,
        String contentHash,
        boolean replayed) {

    static SubmissionResult counted(Long castBallotId, String contentHash, boolean replayed) {
        return new SubmissionResult("COUNTED", castBallotId, null, contentHash, replayed);
    }

    static SubmissionResult provisional(Long provisionalBallotId, String contentHash, boolean replayed) {
        return new SubmissionResult("PROVISIONAL_PENDING", null, provisionalBallotId, contentHash, replayed);
    }
}
