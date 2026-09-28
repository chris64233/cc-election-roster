package com.chris64233.electionroster.service;

import com.chris64233.electionroster.api.CureConfirmationResponse;
import com.chris64233.electionroster.api.CureMaterialResponse;
import com.chris64233.electionroster.api.ExternalVoteResponse;
import com.chris64233.electionroster.api.IssueRequest;
import com.chris64233.electionroster.api.IssuanceResponse;
import com.chris64233.electionroster.api.SubmitRequest;
import com.chris64233.electionroster.api.SubmitResponse;
import com.chris64233.electionroster.domain.BallotContent;
import com.chris64233.electionroster.domain.BallotStyle;
import com.chris64233.electionroster.domain.CureStatus;
import com.chris64233.electionroster.domain.District;
import com.chris64233.electionroster.domain.Election;
import com.chris64233.electionroster.domain.Voter;
import com.chris64233.electionroster.domain.VoterStatus;
import com.chris64233.electionroster.repo.AuditEventRepository;
import com.chris64233.electionroster.repo.BallotContentRepository;
import com.chris64233.electionroster.repo.BallotStyleRepository;
import com.chris64233.electionroster.repo.CureMaterialRepository;
import com.chris64233.electionroster.repo.CureRecordRepository;
import com.chris64233.electionroster.repo.DistrictRepository;
import com.chris64233.electionroster.repo.ElectionRepository;
import com.chris64233.electionroster.repo.ExternalVoteRecordRepository;
import com.chris64233.electionroster.repo.IssuanceRepository;
import com.chris64233.electionroster.repo.ProvisionalRecordRepository;
import com.chris64233.electionroster.repo.SubmissionRecordRepository;
import com.chris64233.electionroster.repo.VoterRepository;
import com.chris64233.electionroster.web.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CureFlowServiceTest {

    @Autowired private ElectionRepository electionRepository;
    @Autowired private DistrictRepository districtRepository;
    @Autowired private BallotStyleRepository ballotStyleRepository;
    @Autowired private VoterRepository voterRepository;
    @Autowired private IssuanceRepository issuanceRepository;
    @Autowired private ProvisionalRecordRepository provisionalRecordRepository;
    @Autowired private BallotContentRepository ballotContentRepository;
    @Autowired private SubmissionRecordRepository submissionRecordRepository;
    @Autowired private CureRecordRepository cureRecordRepository;
    @Autowired private CureMaterialRepository cureMaterialRepository;
    @Autowired private ExternalVoteRecordRepository externalVoteRecordRepository;
    @Autowired private AuditEventRepository auditEventRepository;

    @Autowired private IssuanceService issuanceService;
    @Autowired private BallotService ballotService;
    @Autowired private CureService cureService;
    @Autowired private QueryService queryService;

    private Election election;
    private District districtA;
    private District districtB;
    private final Instant futureDeadline = Instant.now().plus(7, ChronoUnit.DAYS);

    @BeforeEach
    void cleanUp() {
        externalVoteRecordRepository.deleteAllInBatch();
        submissionRecordRepository.deleteAllInBatch();
        cureMaterialRepository.deleteAllInBatch();
        cureRecordRepository.deleteAllInBatch();
        provisionalRecordRepository.deleteAllInBatch();
        ballotContentRepository.deleteAllInBatch();
        issuanceRepository.deleteAllInBatch();
        auditEventRepository.deleteAllInBatch();
        voterRepository.deleteAllInBatch();
        districtRepository.deleteAllInBatch();
        ballotStyleRepository.deleteAllInBatch();
        electionRepository.deleteAllInBatch();

        election = electionRepository.save(new Election("2026 大选", futureDeadline));
        districtA = districtRepository.save(
                new District(election, "D-A", new BallotStyle(election, "STYLE-A")));
        districtB = districtRepository.save(
                new District(election, "D-B", new BallotStyle(election, "STYLE-B")));
    }

    private Voter voter(String ref, VoterStatus status, District district) {
        return voterRepository.save(new Voter(election, ref, district, status));
    }

    private IssuanceResponse issue(String ref, String type, String place) {
        IssueRequest req = new IssueRequest();
        req.setElectionId(election.getId());
        req.setVoterRef(ref);
        req.setEventNo("EVT-" + ref + "-" + type);
        req.setType(type);
        req.setPollingPlace(place);
        return issuanceService.issue(req);
    }

    private SubmitRequest submitReq(String token, boolean incomplete) {
        SubmitRequest req = new SubmitRequest();
        req.setCredentialToken(token);
        req.setChoicesJson("{\"mayor\":\"CANDIDATE_X_SECRET\"}");
        req.setIdentityIncomplete(incomplete);
        req.setMissingMaterials("缺少身份证件");
        return req;
    }

    @Test
    void heldMailBallot_isNotCounted_andOpensPendingCure() {
        voter("M1", VoterStatus.ELIGIBLE, districtA);
        IssuanceResponse issued = issue("M1", "MAIL", "MAIL-OFFICE");

        SubmitResponse resp = ballotService.submit(submitReq(issued.credentialToken(), true));

        assertThat(resp.counted()).isFalse();
        assertThat(resp.held()).isTrue();
        var ballot = ballotContentRepository.findAll().get(0);
        assertThat(ballot.isCounted()).isFalse();
        assertThat(ballot.isHeld()).isTrue();
        var cure = cureRecordRepository.findByIssuanceId(issued.issuanceId()).orElseThrow();
        assertThat(cure.getStatus()).isEqualTo(CureStatus.PENDING);
        assertThat(queryService.districtSummary(districtA.getId()).heldBallots()).isEqualTo(1);
        assertThat(queryService.districtSummary(districtA.getId()).pendingCures()).isEqualTo(1);
        // 只签发了一张票，凭证已消费
        assertThat(issuanceRepository.count()).isEqualTo(1);
    }

    @Test
    void cureMaterial_createsNewVersion_linkedToSameBallot_noReissue() {
        voter("M1", VoterStatus.ELIGIBLE, districtA);
        IssuanceResponse issued = issue("M1", "MAIL", "MAIL-OFFICE");
        ballotService.submit(submitReq(issued.credentialToken(), true));
        String originalToken = issued.credentialToken();

        CureMaterialResponse v1 = cureService.submitMaterial(issued.issuanceId(), "补交水电账单", "DOC-1");
        CureMaterialResponse v2 = cureService.submitMaterial(issued.issuanceId(), "补交驾照", "DOC-2");

        assertThat(v1.version()).isEqualTo(1);
        assertThat(v2.version()).isEqualTo(2);
        // 仍是同一张票、同一凭证，没有重新签发
        assertThat(issuanceRepository.count()).isEqualTo(1);
        assertThat(issuanceRepository.findById(issued.issuanceId()).orElseThrow().getCredentialToken())
                .isEqualTo(originalToken);
    }

    @Test
    void cureConfirm_restoresOriginalBallot_andCountsIt() {
        voter("M1", VoterStatus.ELIGIBLE, districtA);
        IssuanceResponse issued = issue("M1", "MAIL", "MAIL-OFFICE");
        ballotService.submit(submitReq(issued.credentialToken(), true));
        cureService.submitMaterial(issued.issuanceId(), "补交证件", "DOC-1");

        CureConfirmationResponse confirmed = cureService.confirm(issued.issuanceId(), "身份核验通过");

        assertThat(confirmed.restored()).isTrue();
        assertThat(confirmed.cureStatus()).isEqualTo("CONFIRMED");
        var ballot = ballotContentRepository.findAll().get(0);
        assertThat(ballot.isCounted()).isTrue();
        assertThat(ballot.isHeld()).isFalse();
        assertThat(ballotContentRepository.countByDistrictIdAndCountedTrue(districtA.getId())).isEqualTo(1);
        assertThat(queryService.districtSummary(districtA.getId()).heldBallots()).isZero();
        // 仍只有一张票
        assertThat(issuanceRepository.count()).isEqualTo(1);
    }

    @Test
    void confirm_withoutMaterial_conflicts() {
        voter("M1", VoterStatus.ELIGIBLE, districtA);
        IssuanceResponse issued = issue("M1", "MAIL", "MAIL-OFFICE");
        ballotService.submit(submitReq(issued.credentialToken(), true));

        assertThatThrownBy(() -> cureService.confirm(issued.issuanceId(), "无材料"))
                .isInstanceOf(ConflictException.class);
        assertThat(cureRecordRepository.findByIssuanceId(issued.issuanceId()).orElseThrow().getStatus())
                .isEqualTo(CureStatus.PENDING);
    }

    @Test
    void externalVote_makesCureFail_andVoidsHeldBallot() {
        voter("M1", VoterStatus.ELIGIBLE, districtA);
        IssuanceResponse issued = issue("M1", "MAIL", "MAIL-OFFICE");
        ballotService.submit(submitReq(issued.credentialToken(), true));
        cureService.submitMaterial(issued.issuanceId(), "补交证件", "DOC-1");

        ExternalVoteResponse ext = cureService.recordExternalVote(
                election.getId(), "M1", "OTHER_POLLING_PLACE", "EXT-1");
        assertThat(ext.outcome()).isEqualTo("LOCAL_BALLOT_VOIDED");

        assertThatThrownBy(() -> cureService.confirm(issued.issuanceId(), "试图确认"))
                .isInstanceOf(ConflictException.class);

        var cure = cureRecordRepository.findByIssuanceId(issued.issuanceId()).orElseThrow();
        assertThat(cure.getStatus()).isEqualTo(CureStatus.SUPERSEDED);
        var ballot = ballotContentRepository.findAll().get(0);
        assertThat(ballot.isCounted()).isFalse();
        assertThat(ballot.isVoided()).isTrue();
        assertThat(ballotContentRepository.countByDistrictIdAndCountedTrue(districtA.getId())).isZero();
    }

    @Test
    void cureConfirm_thenExternalVote_conflicts_singleValidResult() {
        voter("M1", VoterStatus.ELIGIBLE, districtA);
        IssuanceResponse issued = issue("M1", "MAIL", "MAIL-OFFICE");
        ballotService.submit(submitReq(issued.credentialToken(), true));
        cureService.submitMaterial(issued.issuanceId(), "补交证件", "DOC-1");
        cureService.confirm(issued.issuanceId(), "通过");

        // 本地已形成有效（已计入）选票，外部登记必须失败回滚
        assertThatThrownBy(() -> cureService.recordExternalVote(
                election.getId(), "M1", "OTHER_POLLING_PLACE", "EXT-2"))
                .isInstanceOf(ConflictException.class);
        assertThat(externalVoteRecordRepository.count()).isZero();
        assertThat(ballotContentRepository.countByDistrictIdAndCountedTrue(districtA.getId())).isEqualTo(1);
    }

    @Test
    void duplicateExternalVote_isIdempotent() {
        voter("M1", VoterStatus.ELIGIBLE, districtA);
        ExternalVoteResponse first = cureService.recordExternalVote(
                election.getId(), "M1", "EXT-SYS", "EXT-9");
        ExternalVoteResponse replay = cureService.recordExternalVote(
                election.getId(), "M1", "EXT-SYS", "EXT-9");
        assertThat(replay.outcome()).isEqualTo("DUPLICATE");
        assertThat(first.externalRef()).isEqualTo(replay.externalRef());
        assertThat(externalVoteRecordRepository.count()).isEqualTo(1);
    }

    @Test
    void submitAfterExternalVote_isRejected() {
        voter("M1", VoterStatus.ELIGIBLE, districtA);
        IssuanceResponse issued = issue("M1", "MAIL", "MAIL-OFFICE");
        cureService.recordExternalVote(election.getId(), "M1", "EXT-SYS", "EXT-3");

        assertThatThrownBy(() -> ballotService.submit(submitReq(issued.credentialToken(), false)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void confirmAfterDeadline_expires_andFails() {
        Election expired = electionRepository.save(
                new Election("过期选举", Instant.now().minus(1, ChronoUnit.HOURS)));
        District d = districtRepository.save(new District(expired, "D-X", new BallotStyle(expired, "S-X")));
        voterRepository.save(new Voter(expired, "L1", d, VoterStatus.ELIGIBLE));
        IssueRequest req = new IssueRequest();
        req.setElectionId(expired.getId());
        req.setVoterRef("L1");
        req.setEventNo("E-L1");
        req.setType("MAIL");
        req.setPollingPlace("MAIL");
        IssuanceResponse issued = issuanceService.issue(req);
        SubmitRequest sr = new SubmitRequest();
        sr.setCredentialToken(issued.credentialToken());
        sr.setChoicesJson("{}");
        sr.setIdentityIncomplete(true);
        ballotService.submit(sr);

        // 截止后不能再提交材料
        assertThatThrownBy(() -> cureService.submitMaterial(issued.issuanceId(), "晚到材料", "LATE"))
                .isInstanceOf(ConflictException.class);

        int decided = cureService.expireOverdueCures();
        assertThat(decided).isEqualTo(1);
        var cure = cureRecordRepository.findByIssuanceId(issued.issuanceId()).orElseThrow();
        assertThat(cure.getStatus()).isEqualTo(CureStatus.EXPIRED);
        assertThat(ballotContentRepository.findAll().get(0).isVoided()).isTrue();

        // 逾期确认同样失败
        assertThatThrownBy(() -> cureService.confirm(issued.issuanceId(), "逾期"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void confirm_whenVoterTurnedIneligible_fails_andVoids() {
        voter("M1", VoterStatus.ELIGIBLE, districtA);
        IssuanceResponse issued = issue("M1", "MAIL", "MAIL-OFFICE");
        ballotService.submit(submitReq(issued.credentialToken(), true));
        cureService.submitMaterial(issued.issuanceId(), "补交证件", "DOC-1");

        Voter updated = voterRepository.findByElectionIdAndVoterRef(election.getId(), "M1").orElseThrow();
        updated.setStatus(VoterStatus.INELIGIBLE);
        voterRepository.save(updated);

        assertThatThrownBy(() -> cureService.confirm(issued.issuanceId(), "核资格"))
                .isInstanceOf(ConflictException.class);
        var cure = cureRecordRepository.findByIssuanceId(issued.issuanceId()).orElseThrow();
        assertThat(cure.getStatus()).isEqualTo(CureStatus.REJECTED);
        assertThat(ballotContentRepository.findAll().get(0).isVoided()).isTrue();
    }

    @Test
    void confirm_whenDistrictChanged_rechecksAndFails() {
        voter("M1", VoterStatus.ELIGIBLE, districtA);
        IssuanceResponse issued = issue("M1", "MAIL", "MAIL-OFFICE");
        ballotService.submit(submitReq(issued.credentialToken(), true));
        cureService.submitMaterial(issued.issuanceId(), "补交证件", "DOC-1");

        // 名册选区被改到 B，但原选票属于 A：确认前重新核验选区不一致
        Voter moved = voterRepository.findByElectionIdAndVoterRef(election.getId(), "M1").orElseThrow();
        moved.setDistrict(districtB);
        voterRepository.save(moved);

        assertThatThrownBy(() -> cureService.confirm(issued.issuanceId(), "核选区"))
                .isInstanceOf(ConflictException.class);
        assertThat(cureRecordRepository.findByIssuanceId(issued.issuanceId()).orElseThrow().getStatus())
                .isEqualTo(CureStatus.REJECTED);
    }

    @Test
    void confirmAndExternalVote_race_onlyOneValidResult() throws Exception {
        voter("M1", VoterStatus.ELIGIBLE, districtA);
        IssuanceResponse issued = issue("M1", "MAIL", "MAIL-OFFICE");
        ballotService.submit(submitReq(issued.credentialToken(), true));
        cureService.submitMaterial(issued.issuanceId(), "补交证件", "DOC-1");

        int runs = 12;
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            for (int run = 0; run < runs; run++) {
                final int runIdx = run;
                // 每个 run 用独立选民，避免相互干扰；同一选民内部两线程竞争。
                String ref = "RACE-" + run;
                voter(ref, VoterStatus.ELIGIBLE, districtA);
                IssuanceResponse iss = issue(ref, "MAIL", "MAIL-OFFICE");
                ballotService.submit(submitReq(iss.credentialToken(), true));
                cureService.submitMaterial(iss.issuanceId(), "材料", "D-" + run);

                CountDownLatch start = new CountDownLatch(1);
                AtomicInteger confirmWins = new AtomicInteger();
                AtomicInteger externalWins = new AtomicInteger();

                Future<?> f1 = pool.submit(() -> {
                    try {
                        start.await();
                        cureService.confirm(iss.issuanceId(), "并发确认");
                        confirmWins.incrementAndGet();
                    } catch (Exception ignore) {
                    }
                });
                Future<?> f2 = pool.submit(() -> {
                    try {
                        start.await();
                        cureService.recordExternalVote(election.getId(), ref, "EXT", "X-" + runIdx);
                        externalWins.incrementAndGet();
                    } catch (Exception ignore) {
                    }
                });
                start.countDown();
                f1.get();
                f2.get();

                // 恰一个赢家
                assertThat(confirmWins.get() + externalWins.get()).isEqualTo(1);

                String ballotId = submissionRecordRepository
                        .findByCredentialToken(iss.credentialToken()).orElseThrow().getBallotId();
                boolean counted = ballotContentRepository.findById(ballotId).orElseThrow().isCounted();
                boolean external = externalVoteRecordRepository
                        .findByElectionIdAndVoterId(election.getId(),
                                voterRepository.findByElectionIdAndVoterRef(election.getId(), ref)
                                        .orElseThrow().getId())
                        .isPresent();
                // 已计入选票与外部有效结果互斥：恰一个成立
                assertThat(counted ^ external).isTrue();
            }
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void cureIsolation_statusAndAudit_doNotExposeBallotChoicesOrMaterials() {
        voter("M1", VoterStatus.ELIGIBLE, districtA);
        IssuanceResponse issued = issue("M1", "MAIL", "MAIL-OFFICE");
        ballotService.submit(submitReq(issued.credentialToken(), true));
        cureService.submitMaterial(issued.issuanceId(), "护照号 SECRET-ID-99", "SECRET-DOC");
        cureService.confirm(issued.issuanceId(), "通过");

        // 状态接口只暴露补正状态，不含材料/缺件/票面
        var status = queryService.voterStatus(election.getId(), "M1");
        assertThat(status.cureStatus()).isEqualTo("CONFIRMED");
        String statusJson = status.toString();
        assertThat(statusJson).doesNotContain("CANDIDATE_X_SECRET");
        assertThat(statusJson).doesNotContain("SECRET-ID-99");
        assertThat(statusJson).doesNotContain("缺少身份证件");

        // 审计链绝不出现票面选择或材料正文
        var chain = queryService.auditChain();
        String auditText = chain.toString();
        assertThat(auditText).doesNotContain("CANDIDATE_X_SECRET");
        assertThat(auditText).doesNotContain("SECRET-DOC");
        assertThat(auditText).doesNotContain("SECRET-ID-99");
        // 但允许出现材料/票面的哈希
        assertThat(chain).extracting(QueryService.AuditEventView::eventType)
                .contains("CURE_MATERIAL", "CURE_CONFIRM");
    }
}
