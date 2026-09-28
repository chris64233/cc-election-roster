package com.chris64233.electionroster.service;

import com.chris64233.electionroster.api.AdjudicationResponse;
import com.chris64233.electionroster.api.IssueRequest;
import com.chris64233.electionroster.api.IssuanceResponse;
import com.chris64233.electionroster.api.SubmitRequest;
import com.chris64233.electionroster.api.SubmitResponse;
import com.chris64233.electionroster.domain.Adjudication;
import com.chris64233.electionroster.domain.BallotStyle;
import com.chris64233.electionroster.domain.District;
import com.chris64233.electionroster.domain.Election;
import com.chris64233.electionroster.domain.Voter;
import com.chris64233.electionroster.domain.VoterStatus;
import com.chris64233.electionroster.repo.AuditEventRepository;
import com.chris64233.electionroster.repo.BallotContentRepository;
import com.chris64233.electionroster.repo.BallotStyleRepository;
import com.chris64233.electionroster.repo.DistrictRepository;
import com.chris64233.electionroster.repo.ElectionRepository;
import com.chris64233.electionroster.repo.IssuanceRepository;
import com.chris64233.electionroster.repo.ProvisionalRecordRepository;
import com.chris64233.electionroster.repo.SubmissionRecordRepository;
import com.chris64233.electionroster.repo.VoterRepository;
import com.chris64233.electionroster.web.ConflictException;
import com.chris64233.electionroster.web.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ElectionFlowServiceTest {

    @Autowired
    private ElectionRepository electionRepository;
    @Autowired
    private DistrictRepository districtRepository;
    @Autowired
    private BallotStyleRepository ballotStyleRepository;
    @Autowired
    private VoterRepository voterRepository;
    @Autowired
    private IssuanceRepository issuanceRepository;
    @Autowired
    private ProvisionalRecordRepository provisionalRecordRepository;
    @Autowired
    private com.chris64233.electionroster.repo.CureRecordRepository cureRecordRepository;
    @Autowired
    private BallotContentRepository ballotContentRepository;
    @Autowired
    private SubmissionRecordRepository submissionRecordRepository;
    @Autowired
    private com.chris64233.electionroster.repo.EffectiveVoteRepository effectiveVoteRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private IssuanceService issuanceService;
    @Autowired
    private BallotService ballotService;
    @Autowired
    private AdjudicationService adjudicationService;
    @Autowired
    private QueryService queryService;

    private Election election;
    private District districtA;
    private District districtB;

    @BeforeEach
    void cleanUp() {
        submissionRecordRepository.deleteAllInBatch();
        cureRecordRepository.deleteAllInBatch();
        provisionalRecordRepository.deleteAllInBatch();
        effectiveVoteRepository.deleteAllInBatch();
        ballotContentRepository.deleteAllInBatch();
        issuanceRepository.deleteAllInBatch();
        auditEventRepository.deleteAllInBatch();
        voterRepository.deleteAllInBatch();
        districtRepository.deleteAllInBatch();
        ballotStyleRepository.deleteAllInBatch();
        electionRepository.deleteAllInBatch();

        election = electionRepository.save(new Election("2026 大选"));
        districtA = districtRepository.save(
                new District(election, "D-A", new BallotStyle(election, "STYLE-A")));
        districtB = districtRepository.save(
                new District(election, "D-B", new BallotStyle(election, "STYLE-B")));
    }

    private Voter registerVoter(String ref, District district, VoterStatus status) {
        return voterRepository.save(new Voter(election, ref, district, status));
    }

    private IssueRequest issueRequest(String ref, String eventNo, String type, String pollingPlace) {
        IssueRequest request = new IssueRequest();
        request.setElectionId(election.getId());
        request.setVoterRef(ref);
        request.setEventNo(eventNo);
        request.setType(type);
        request.setPollingPlace(pollingPlace);
        return request;
    }

    private SubmitRequest submitRequest(String token, String choices) {
        SubmitRequest request = new SubmitRequest();
        request.setCredentialToken(token);
        request.setChoicesJson(choices);
        return request;
    }

    @Test
    void officialIssuance_verifiesRoster_andReturnsImmutableCredential() {
        registerVoter("V1", districtA, VoterStatus.ELIGIBLE);

        IssuanceResponse response = issuanceService.issue(
                issueRequest("V1", "EVT-1", "OFFICIAL", "P-01"));

        assertThat(response.credentialToken()).isNotBlank();
        assertThat(response.type()).isEqualTo("OFFICIAL");
        assertThat(response.status()).isEqualTo("ISSUED");
        assertThat(response.districtCode()).isEqualTo("D-A");
        assertThat(response.ballotStyleCode()).isEqualTo("STYLE-A");

        var status = queryService.voterStatus(election.getId(), "V1");
        assertThat(status.issued()).isTrue();
        assertThat(status.issuanceType()).isEqualTo("OFFICIAL");
        assertThat(status.issuanceStatus()).isEqualTo("ISSUED");
        assertThat(status.pollingPlace()).isEqualTo("P-01");
    }

    @Test
    void issuance_isIdempotent_byEventNo() {
        registerVoter("V1", districtA, VoterStatus.ELIGIBLE);

        IssuanceResponse first = issuanceService.issue(
                issueRequest("V1", "EVT-X", "OFFICIAL", "P-01"));
        IssuanceResponse replay = issuanceService.issue(
                issueRequest("V1", "EVT-X", "OFFICIAL", "P-01"));

        assertThat(replay.credentialToken()).isEqualTo(first.credentialToken());
        assertThat(issuanceRepository.count()).isEqualTo(1);
    }

    @Test
    void eventNo_reusedByDifferentVoter_conflicts() {
        registerVoter("V1", districtA, VoterStatus.ELIGIBLE);
        registerVoter("V2", districtA, VoterStatus.ELIGIBLE);

        issuanceService.issue(issueRequest("V1", "EVT-SAME", "OFFICIAL", "P-01"));
        assertThatThrownBy(() -> issuanceService.issue(
                issueRequest("V2", "EVT-SAME", "OFFICIAL", "P-02")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void oneVoter_onlyOneBallot_acrossOfficialProvisionalAndMail() {
        registerVoter("V1", districtA, VoterStatus.ELIGIBLE);

        issuanceService.issue(issueRequest("V1", "EVT-OFF", "OFFICIAL", "P-01"));

        // 不同事件号、不同投票点、不同类型：全部被同一共享唯一约束拒绝
        assertThatThrownBy(() -> issuanceService.issue(
                issueRequest("V1", "EVT-MAIL", "MAIL", "MAIL-OFFICE")))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> issuanceService.issue(
                issueRequest("V1", "EVT-PROV", "PROVISIONAL", "P-02")))
                .isInstanceOf(ConflictException.class);
        assertThat(issuanceRepository.count()).isEqualTo(1);
    }

    @Test
    void disputedVoter_canOnlyGetProvisional() {
        registerVoter("V9", districtA, VoterStatus.DISPUTED);

        assertThatThrownBy(() -> issuanceService.issue(
                issueRequest("V9", "EVT-BAD", "OFFICIAL", "P-01")))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> issuanceService.issue(
                issueRequest("V9", "EVT-MAIL", "MAIL", "MAIL-OFFICE")))
                .isInstanceOf(ConflictException.class);

        IssueRequest provisional = issueRequest("V9", "EVT-PROV", "PROVISIONAL", "P-01");
        provisional.setIdentityNotes("地址证明待核验");
        IssuanceResponse response = issuanceService.issue(provisional);

        assertThat(response.type()).isEqualTo("PROVISIONAL");
        var record = provisionalRecordRepository.findByIssuanceId(response.issuanceId()).orElseThrow();
        assertThat(record.getAdjudication()).isEqualTo(Adjudication.PENDING);
        assertThat(record.getIdentityNotes()).isEqualTo("地址证明待核验");

        var status = queryService.voterStatus(election.getId(), "V9");
        assertThat(status.adjudication()).isEqualTo("PENDING");
    }

    @Test
    void ineligibleVoter_getsNothing() {
        registerVoter("V0", districtA, VoterStatus.INELIGIBLE);
        assertThatThrownBy(() -> issuanceService.issue(
                issueRequest("V0", "EVT-0", "OFFICIAL", "P-01")))
                .isInstanceOf(ConflictException.class);
        assertThat(issuanceRepository.count()).isZero();
    }

    @Test
    void districtMismatch_rejectsIssuance() {
        registerVoter("V1", districtA, VoterStatus.ELIGIBLE);
        IssueRequest request = issueRequest("V1", "EVT-MIS", "OFFICIAL", "P-01");
        request.setExpectedDistrictCode("D-B");
        assertThatThrownBy(() -> issuanceService.issue(request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void unknownVoter_rejectsWithNotFound() {
        assertThatThrownBy(() -> issuanceService.issue(
                issueRequest("GHOST", "EVT-G", "OFFICIAL", "P-01")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void concurrentIssuanceFromDifferentPollingPlaces_atMostOneSucceeds() throws Exception {
        registerVoter("V1", districtA, VoterStatus.ELIGIBLE);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            var results = pool.invokeAll(IntStream.range(0, threads).<Callable<Void>>mapToObj(i -> () -> {
                issuanceService.issue(issueRequest(
                        "V1", "EVT-C-" + i, "OFFICIAL", "P-0" + i));
                return null;
            }).toList());

            long success = 0;
            long conflicts = 0;
            for (var future : results) {
                try {
                    future.get();
                    success++;
                } catch (Exception e) {
                    assertThat(e.getCause()).isInstanceOf(ConflictException.class);
                    conflicts++;
                }
            }
            assertThat(success).isEqualTo(1);
            assertThat(conflicts).isEqualTo(threads - 1);
            assertThat(issuanceRepository.count()).isEqualTo(1);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void submit_consumesCredentialOnce_replaySameContent_returnsOriginalReceipt() {
        registerVoter("V1", districtA, VoterStatus.ELIGIBLE);
        IssuanceResponse issued = issuanceService.issue(
                issueRequest("V1", "EVT-1", "OFFICIAL", "P-01"));

        SubmitResponse first = ballotService.submit(
                submitRequest(issued.credentialToken(), "{\"mayor\":\"ALICE_SECRET\"}"));
        assertThat(first.counted()).isTrue();
        assertThat(first.duplicate()).isFalse();

        // 相同内容重放：返回原回执
        SubmitResponse replay = ballotService.submit(
                submitRequest(issued.credentialToken(), "{\"mayor\":\"ALICE_SECRET\"}"));
        assertThat(replay.duplicate()).isTrue();
        assertThat(replay.receiptId()).isEqualTo(first.receiptId());
        assertThat(submissionRecordRepository.count()).isEqualTo(1);
    }

    @Test
    void submit_credentialReusedWithDifferentContent_conflicts() {
        registerVoter("V1", districtA, VoterStatus.ELIGIBLE);
        IssuanceResponse issued = issuanceService.issue(
                issueRequest("V1", "EVT-1", "OFFICIAL", "P-01"));

        ballotService.submit(submitRequest(issued.credentialToken(), "{\"mayor\":\"ALICE_SECRET\"}"));
        assertThatThrownBy(() -> ballotService.submit(
                submitRequest(issued.credentialToken(), "{\"mayor\":\"BOB_SECRET\"}")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void submit_unknownCredential_notFound() {
        assertThatThrownBy(() -> ballotService.submit(
                submitRequest("no-such-token", "{}")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void provisionalBallot_untilAccepted_isNotCounted() {
        registerVoter("V9", districtA, VoterStatus.DISPUTED);
        IssueRequest provisional = issueRequest("V9", "EVT-P", "PROVISIONAL", "P-01");
        provisional.setIdentityNotes("地址存疑");
        IssuanceResponse issued = issuanceService.issue(provisional);

        SubmitResponse submitted = ballotService.submit(
                submitRequest(issued.credentialToken(), "{\"mayor\":\"ALICE_SECRET\"}"));
        assertThat(submitted.counted()).isFalse();
        assertThat(ballotContentRepository.countByDistrictIdAndCountedTrue(districtA.getId())).isZero();

        AdjudicationResponse accepted = adjudicationService.adjudicate(
                issued.issuanceId(), true, "地址核验通过");
        assertThat(accepted.adjudication()).isEqualTo("ACCEPTED");
        assertThat(ballotContentRepository.countByDistrictIdAndCountedTrue(districtA.getId())).isEqualTo(1);

        // 裁定不可逆
        assertThatThrownBy(() -> adjudicationService.adjudicate(
                issued.issuanceId(), false, "反悔"))
                .isInstanceOf(IllegalStateException.class);

        var status = queryService.voterStatus(election.getId(), "V9");
        assertThat(status.adjudication()).isEqualTo("ACCEPTED");
        assertThat(status.issuanceStatus()).isEqualTo("CONSUMED");
    }

    @Test
    void provisionalRejected_afterSubmit_voidsBallotForever() {
        registerVoter("V9", districtA, VoterStatus.DISPUTED);
        IssuanceResponse issued = issuanceService.issue(
                issueRequest("V9", "EVT-P", "PROVISIONAL", "P-01"));
        ballotService.submit(submitRequest(issued.credentialToken(), "{\"mayor\":\"X\"}"));

        adjudicationService.adjudicate(issued.issuanceId(), false, "查无此人");

        var ballot = ballotContentRepository.findAll().get(0);
        assertThat(ballot.isCounted()).isFalse();
        assertThat(ballot.isVoided()).isTrue();
        assertThat(ballotContentRepository.countByDistrictIdAndCountedTrue(districtA.getId())).isZero();
    }

    @Test
    void provisionalRejected_beforeSubmit_voidsCredentialAndBlocksSubmission() {
        registerVoter("V9", districtA, VoterStatus.DISPUTED);
        IssuanceResponse issued = issuanceService.issue(
                issueRequest("V9", "EVT-P", "PROVISIONAL", "P-01"));

        adjudicationService.adjudicate(issued.issuanceId(), false, "资格不成立");
        assertThatThrownBy(() -> ballotService.submit(
                submitRequest(issued.credentialToken(), "{}")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void districtSummary_countsOnlyCountedBallots_andPendingProvisionals() {
        registerVoter("V1", districtA, VoterStatus.ELIGIBLE);
        registerVoter("V2", districtA, VoterStatus.ELIGIBLE);
        registerVoter("V9", districtA, VoterStatus.DISPUTED);
        registerVoter("W1", districtB, VoterStatus.ELIGIBLE);

        IssuanceResponse i1 = issuanceService.issue(issueRequest("V1", "E1", "OFFICIAL", "P-01"));
        issuanceService.issue(issueRequest("V2", "E2", "OFFICIAL", "P-01"));
        IssuanceResponse i9 = issuanceService.issue(issueRequest("V9", "E9", "PROVISIONAL", "P-01"));
        IssuanceResponse w1 = issuanceService.issue(issueRequest("W1", "EW", "MAIL", "MAIL-OFFICE"));

        ballotService.submit(submitRequest(i1.credentialToken(), "{}"));
        ballotService.submit(submitRequest(i9.credentialToken(), "{}"));
        ballotService.submit(submitRequest(w1.credentialToken(), "{}"));

        var summaryA = queryService.districtSummary(districtA.getId());
        assertThat(summaryA.districtCode()).isEqualTo("D-A");
        assertThat(summaryA.issuedTotal()).isEqualTo(3);
        assertThat(summaryA.consumed()).isEqualTo(2);
        assertThat(summaryA.countedBallots()).isEqualTo(1); // V1；临时票未裁定
        assertThat(summaryA.pendingProvisionals()).isEqualTo(1);

        var summaryB = queryService.districtSummary(districtB.getId());
        assertThat(summaryB.countedBallots()).isEqualTo(1); // 邮寄票只计入本选区
        assertThat(summaryB.pendingProvisionals()).isZero();
    }

    @Test
    void auditChain_isHashLinked_andNeverExposesChoices() {
        registerVoter("V1", districtA, VoterStatus.ELIGIBLE);
        IssuanceResponse issued = issuanceService.issue(
                issueRequest("V1", "EVT-1", "OFFICIAL", "P-01"));
        ballotService.submit(submitRequest(issued.credentialToken(), "{\"mayor\":\"ALICE_SECRET\"}"));

        var chain = queryService.auditChain();
        assertThat(chain).hasSize(2);
        assertThat(chain).extracting(QueryService.AuditEventView::eventType)
                .containsExactly("ISSUE", "SUBMIT");

        // 哈希链可独立重算验证
        String prev = "0".repeat(64);
        for (var event : chain) {
            assertThat(event.prevHash()).isEqualTo(prev);
            String recomputed = AuditService.sha256(
                    prev + "|" + event.eventType() + "|" + event.refId() + "|" + event.detail());
            assertThat(event.eventHash()).isEqualTo(recomputed);
            prev = event.eventHash();
        }

        // 选择内容绝不出现在审计链中
        assertThat(chain).extracting(QueryService.AuditEventView::detail)
                .noneMatch(detail -> detail.contains("ALICE_SECRET"));
    }
}
