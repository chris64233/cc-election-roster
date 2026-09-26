package com.chris64233.electionroster;

import com.chris64233.electionroster.domain.Election;
import com.chris64233.electionroster.domain.VoterStatus;
import com.chris64233.electionroster.service.AdjudicationService;
import com.chris64233.electionroster.service.BallotSubmissionService;
import com.chris64233.electionroster.service.ConflictException;
import com.chris64233.electionroster.service.IssuanceResult;
import com.chris64233.electionroster.service.IssuanceService;
import com.chris64233.electionroster.service.IssuanceService.IssueCommand;
import com.chris64233.electionroster.service.QueryService;
import com.chris64233.electionroster.service.SubmissionResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BallotSubmissionServiceTest extends ElectionTestSupport {

    @Autowired
    private IssuanceService issuanceService;
    @Autowired
    private BallotSubmissionService submissionService;
    @Autowired
    private AdjudicationService adjudicationService;
    @Autowired
    private QueryService queryService;

    private IssuanceResult issueOfficial(Long electionId, String voterRef, String eventId) {
        return issuanceService.issue(electionId,
                new IssueCommand(voterRef, eventId, null, "P1", "投票站A", null, null, null));
    }

    @Test
    void officialBallotIsCountedIntoPrecinct() {
        Election election = newElection();
        setupBallotStructure(election.getId());
        registerVoter(election.getId(), "V101", VoterStatus.ELIGIBLE);
        IssuanceResult issuance = issueOfficial(election.getId(), "V101", "evt-s1");

        SubmissionResult result = submissionService.submit(issuance.credentialToken(), "候选人=甲");

        assertThat(result.outcome()).isEqualTo("COUNTED");
        assertThat(result.castBallotId()).isNotNull();

        QueryService.PrecinctSummaryView summary = queryService.precinctSummary(election.getId(), "P1");
        assertThat(summary.countedOfficial()).isEqualTo(1);
        assertThat(summary.countedTotal()).isEqualTo(1);
    }

    @Test
    void duplicateSubmissionWithSameContentReturnsOriginalResult() {
        Election election = newElection();
        setupBallotStructure(election.getId());
        registerVoter(election.getId(), "V102", VoterStatus.ELIGIBLE);
        IssuanceResult issuance = issueOfficial(election.getId(), "V102", "evt-s2");

        SubmissionResult first = submissionService.submit(issuance.credentialToken(), "候选人=乙");
        SubmissionResult replay = submissionService.submit(issuance.credentialToken(), "候选人=乙");

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.contentHash()).isEqualTo(first.contentHash());
        // 仍然只计入一次
        assertThat(queryService.precinctSummary(election.getId(), "P1").countedTotal()).isEqualTo(1);
    }

    @Test
    void duplicateSubmissionWithDifferentContentConflicts() {
        Election election = newElection();
        setupBallotStructure(election.getId());
        registerVoter(election.getId(), "V103", VoterStatus.ELIGIBLE);
        IssuanceResult issuance = issueOfficial(election.getId(), "V103", "evt-s3");

        submissionService.submit(issuance.credentialToken(), "候选人=甲");

        assertThatThrownBy(() -> submissionService.submit(issuance.credentialToken(), "候选人=丙"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("不同");
        // 原结果不被修改
        assertThat(queryService.precinctSummary(election.getId(), "P1").countedTotal()).isEqualTo(1);
    }

    @Test
    void provisionalBallotWaitsForAdjudicationThenCounts() {
        Election election = newElection();
        setupBallotStructure(election.getId());
        registerVoter(election.getId(), "V104", VoterStatus.DISPUTED);
        IssuanceResult issuance = issuanceService.issue(election.getId(),
                new IssueCommand("V104", "evt-s4", null, "P1", "投票站B",
                        "身份证", "officer-1", null));

        SubmissionResult submitted = submissionService.submit(issuance.credentialToken(), "候选人=丁");
        assertThat(submitted.outcome()).isEqualTo("PROVISIONAL_PENDING");
        assertThat(submitted.provisionalBallotId()).isNotNull();

        // 裁定前不计入选区汇总
        QueryService.PrecinctSummaryView before = queryService.precinctSummary(election.getId(), "P1");
        assertThat(before.countedTotal()).isZero();
        assertThat(before.pendingProvisional()).isEqualTo(1);

        AdjudicationService.AdjudicationResult adjudicated =
                adjudicationService.adjudicate(submitted.provisionalBallotId(), true, "资格确认");
        assertThat(adjudicated.status()).isEqualTo("ACCEPTED");
        assertThat(adjudicated.castBallotId()).isNotNull();

        // 裁定通过后计入对应选区
        QueryService.PrecinctSummaryView after = queryService.precinctSummary(election.getId(), "P1");
        assertThat(after.countedProvisional()).isEqualTo(1);
        assertThat(after.countedTotal()).isEqualTo(1);
        assertThat(after.pendingProvisional()).isZero();
    }

    @Test
    void rejectedProvisionalIsPermanentlyVoided() {
        Election election = newElection();
        setupBallotStructure(election.getId());
        registerVoter(election.getId(), "V105", VoterStatus.DISPUTED);
        IssuanceResult issuance = issuanceService.issue(election.getId(),
                new IssueCommand("V105", "evt-s5", null, "P1", "投票站B",
                        "身份证", "officer-2", null));
        SubmissionResult submitted = submissionService.submit(issuance.credentialToken(), "候选人=戊");

        adjudicationService.adjudicate(submitted.provisionalBallotId(), false, "资格不成立");

        QueryService.PrecinctSummaryView summary = queryService.precinctSummary(election.getId(), "P1");
        assertThat(summary.countedTotal()).isZero();
        assertThat(summary.rejectedProvisional()).isEqualTo(1);

        // 作废后的凭证不能再提交
        assertThatThrownBy(() -> submissionService.submit(issuance.credentialToken(), "候选人=戊"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("作废");
    }

    @Test
    void adjudicationIsFinal() {
        Election election = newElection();
        setupBallotStructure(election.getId());
        registerVoter(election.getId(), "V106", VoterStatus.DISPUTED);
        IssuanceResult issuance = issuanceService.issue(election.getId(),
                new IssueCommand("V106", "evt-s6", null, "P1", "投票站B",
                        "身份证", "officer-3", null));
        SubmissionResult submitted = submissionService.submit(issuance.credentialToken(), "候选人=己");

        adjudicationService.adjudicate(submitted.provisionalBallotId(), true, "资格确认");

        assertThatThrownBy(() ->
                adjudicationService.adjudicate(submitted.provisionalBallotId(), false, "改判"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void unknownCredentialIsRejected() {
        Election election = newElection();
        setupBallotStructure(election.getId());

        assertThatThrownBy(() -> submissionService.submit("no-such-token", "候选人=甲"))
                .isInstanceOf(com.chris64233.electionroster.service.NotFoundException.class);
    }
}
