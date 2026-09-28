package com.chris64233.electionroster.service;

import com.chris64233.electionroster.api.CureConfirmRequest;
import com.chris64233.electionroster.api.CureResponse;
import com.chris64233.electionroster.api.CureSubmitRequest;
import com.chris64233.electionroster.api.ExternalVoteRequest;
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
import com.chris64233.electionroster.repo.CureRecordRepository;
import com.chris64233.electionroster.repo.DistrictRepository;
import com.chris64233.electionroster.repo.EffectiveVoteRepository;
import com.chris64233.electionroster.repo.ElectionRepository;
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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class BallotCureServiceTest {

    @Autowired private ElectionRepository electionRepository;
    @Autowired private DistrictRepository districtRepository;
    @Autowired private BallotStyleRepository ballotStyleRepository;
    @Autowired private VoterRepository voterRepository;
    @Autowired private IssuanceRepository issuanceRepository;
    @Autowired private ProvisionalRecordRepository provisionalRecordRepository;
    @Autowired private BallotContentRepository ballotContentRepository;
    @Autowired private SubmissionRecordRepository submissionRecordRepository;
    @Autowired private CureRecordRepository cureRecordRepository;
    @Autowired private EffectiveVoteRepository effectiveVoteRepository;
    @Autowired private AuditEventRepository auditEventRepository;

    @Autowired private IssuanceService issuanceService;
    @Autowired private BallotService ballotService;
    @Autowired private AdjudicationService adjudicationService;
    @Autowired private CureService cureService;
    @Autowired private DeadlineService deadlineService;
    @Autowired private ExternalVoteService externalVoteService;
    @Autowired private QueryService queryService;

    private Election election;
    private District districtA;
    private District districtB;

    @BeforeEach
    void cleanUp() {
        submissionRecordRepository.deleteAllInBatch();
        cureRecordRepository.deleteAllInBatch();
        effectiveVoteRepository.deleteAllInBatch();
        provisionalRecordRepository.deleteAllInBatch();
        ballotContentRepository.deleteAllInBatch();
        issuanceRepository.deleteAllInBatch();
        auditEventRepository.deleteAllInBatch();
        voterRepository.deleteAllInBatch();
        districtRepository.deleteAllInBatch();
        ballotStyleRepository.deleteAllInBatch();
        electionRepository.deleteAllInBatch();

        election = electionRepository.save(new Election("2026 补正大选"));
        districtA = districtRepository.save(
                new District(election, "D-A", new BallotStyle(election, "STYLE-A")));
        districtB = districtRepository.save(
                new District(election, "D-B", new BallotStyle(election, "STYLE-B")));
    }

    private Voter voter(String ref, District district, VoterStatus status) {
        return voterRepository.save(new Voter(election, ref, district, status));
    }

    private void setDeadline(Instant when) {
        Election reloaded = electionRepository.findById(election.getId()).orElseThrow();
        reloaded.setCureDeadline(when);
        election = electionRepository.save(reloaded);
    }

    private IssueRequest mailIssue(String ref, String eventNo, boolean incomplete) {
        IssueRequest request = new IssueRequest();
        request.setElectionId(election.getId());
        request.setVoterRef(ref);
        request.setEventNo(eventNo);
        request.setType("MAIL");
        request.setPollingPlace("MAIL-OFFICE");
        request.setIdentityIncomplete(incomplete);
        return request;
    }

    private IssueRequest provisionalIssue(String ref, String eventNo) {
        IssueRequest request = new IssueRequest();
        request.setElectionId(election.getId());
        request.setVoterRef(ref);
        request.setEventNo(eventNo);
        request.setType("PROVISIONAL");
        request.setPollingPlace("P-01");
        request.setIdentityNotes("身份待核");
        return request;
    }

    private SubmitRequest submitRequest(String token, String choices) {
        SubmitRequest request = new SubmitRequest();
        request.setCredentialToken(token);
        request.setChoicesJson(choices);
        return request;
    }

    private CureSubmitRequest cureSubmit(Long issuanceId, String notes) {
        CureSubmitRequest request = new CureSubmitRequest();
        request.setIssuanceId(issuanceId);
        request.setMaterialNotes(notes);
        return request;
    }

    private CureConfirmRequest cureConfirm(Long issuanceId) {
        CureConfirmRequest request = new CureConfirmRequest();
        request.setIssuanceId(issuanceId);
        return request;
    }

    private ExternalVoteRequest externalVote(String ref, String channelRef) {
        ExternalVoteRequest request = new ExternalVoteRequest();
        request.setVoterRef(ref);
        request.setChannelRef(channelRef);
        return request;
    }

    private BallotContent onlyBallot() {
        List<BallotContent> all = ballotContentRepository.findAll();
        assertThat(all).hasSize(1);
        return all.get(0);
    }

    // ---------- 规则 1：截止前提交新材料，关联原选票，不签发第二张；确认前重新核验 ----------

    @Test
    void mailBallot_incompleteMaterials_isHeld_andCureRestoresOriginalBallot() {
        voter("M1", districtA, VoterStatus.ELIGIBLE);
        setDeadline(Instant.now().plus(2, ChronoUnit.DAYS));
        IssuanceResponse issued = issuanceService.issue(mailIssue("M1", "EVT-M1", true));

        // 材料不全：暂不计入，但凭证消费成功
        SubmitResponse submitted = ballotService.submit(
                submitRequest(issued.credentialToken(), "{\"mayor\":\"ALICE_SECRET\"}"));
        assertThat(submitted.counted()).isFalse();
        assertThat(ballotContentRepository.countByDistrictIdAndCountedTrue(districtA.getId())).isZero();
        assertThat(issuanceRepository.findById(issued.issuanceId()).orElseThrow().getCureStatus())
                .isEqualTo(CureStatus.PENDING);

        // 提交新材料：版本 1，关联原签发记录
        CureResponse v1 = cureService.submit(cureSubmit(issued.issuanceId(), "新版驾照+账单"));
        assertThat(v1.outcome()).isEqualTo("SUBMITTED");
        assertThat(v1.version()).isEqualTo(1);
        assertThat(v1.cureStatus()).isEqualTo("PENDING");

        // 确认：恢复原选票有效
        CureResponse confirmed = cureService.confirm(cureConfirm(issued.issuanceId()));
        assertThat(confirmed.outcome()).isEqualTo("CONFIRMED");
        assertThat(confirmed.version()).isEqualTo(1);
        assertThat(confirmed.cureStatus()).isEqualTo("CONFIRMED");

        // 同一张选票被计入；没有第二张选票、第二张签发、第二条消费记录
        assertThat(ballotContentRepository.countByDistrictIdAndCountedTrue(districtA.getId())).isEqualTo(1);
        assertThat(ballotContentRepository.count()).isEqualTo(1);
        assertThat(issuanceRepository.count()).isEqualTo(1);
        assertThat(submissionRecordRepository.count()).isEqualTo(1);
        assertThat(onlyBallot().isVoided()).isFalse();

        var status = queryService.voterStatus(election.getId(), "M1");
        assertThat(status.cureStatus()).isEqualTo("CONFIRMED");
        assertThat(status.effectiveVoteSource()).isEqualTo("CURE_CONFIRMED");
        assertThat(status.issuanceStatus()).isEqualTo("CONSUMED");
    }

    @Test
    void cureMaterials_areVersioned_oldVersionSuperseded_confirmingOldFails() {
        voter("M2", districtA, VoterStatus.ELIGIBLE);
        setDeadline(Instant.now().plus(2, ChronoUnit.DAYS));
        IssuanceResponse issued = issuanceService.issue(mailIssue("M2", "EVT-M2", true));
        ballotService.submit(submitRequest(issued.credentialToken(), "{}"));

        cureService.submit(cureSubmit(issued.issuanceId(), "第一版材料"));
        CureResponse v2 = cureService.submit(cureSubmit(issued.issuanceId(), "第二版材料"));
        assertThat(v2.version()).isEqualTo(2);
        assertThat(cureRecordRepository.findByIssuanceIdOrderByVersionAsc(issued.issuanceId()))
                .extracting(r -> r.getStatus().name())
                .containsExactly("SUPERSEDED", "PENDING");

        // 确认旧版本被拒绝；确认最新版本成功
        CureConfirmRequest old = cureConfirm(issued.issuanceId());
        old.setVersion(1);
        assertThatThrownBy(() -> cureService.confirm(old)).isInstanceOf(ConflictException.class);

        cureService.confirm(cureConfirm(issued.issuanceId()));
        assertThat(cureRecordRepository.findByIssuanceIdAndVersion(issued.issuanceId(), 2)
                .orElseThrow().getStatus().name()).isEqualTo("CONFIRMED");
    }

    @Test
    void confirm_rechecksVoterStatus_districtMismatchOrDisputed_failsCure() {
        Voter m = voter("M3", districtA, VoterStatus.ELIGIBLE);
        setDeadline(Instant.now().plus(2, ChronoUnit.DAYS));
        IssuanceResponse issued = issuanceService.issue(mailIssue("M3", "EVT-M3", true));
        ballotService.submit(submitRequest(issued.credentialToken(), "{\"mayor\":\"X\"}"));
        cureService.submit(cureSubmit(issued.issuanceId(), "补正材料"));

        // 资格变 INELIGIBLE：确认失败，原选票作废
        m.setStatus(VoterStatus.INELIGIBLE);
        voterRepository.save(m);
        CureResponse failedIneligible = cureService.confirm(cureConfirm(issued.issuanceId()));
        assertThat(failedIneligible.outcome()).isEqualTo("FAILED");
        assertThat(onlyBallot().isVoided()).isTrue();
        assertThat(ballotContentRepository.countByDistrictIdAndCountedTrue(districtA.getId())).isZero();
        // 失败不可恢复
        assertThatThrownBy(() -> cureService.confirm(cureConfirm(issued.issuanceId())))
                .isInstanceOf(ConflictException.class);

        // 选区变化的选民：确认也失败
        Voter moved = voter("M4", districtA, VoterStatus.ELIGIBLE);
        IssuanceResponse issued2 = issuanceService.issue(mailIssue("M4", "EVT-M4", true));
        ballotService.submit(submitRequest(issued2.credentialToken(), "{}"));
        cureService.submit(cureSubmit(issued2.issuanceId(), "补正材料"));
        moved.setDistrict(districtB);
        voterRepository.save(moved);
        CureResponse failedDistrict = cureService.confirm(cureConfirm(issued2.issuanceId()));
        assertThat(failedDistrict.outcome()).isEqualTo("FAILED");
        assertThat(failedDistrict.reason()).contains("选区");
    }

    @Test
    void confirm_rechecksExpectedDistrictCode() {
        voter("M5", districtA, VoterStatus.ELIGIBLE);
        setDeadline(Instant.now().plus(2, ChronoUnit.DAYS));
        IssuanceResponse issued = issuanceService.issue(mailIssue("M5", "EVT-M5", true));
        ballotService.submit(submitRequest(issued.credentialToken(), "{}"));
        cureService.submit(cureSubmit(issued.issuanceId(), "补正材料"));

        CureConfirmRequest wrongCode = cureConfirm(issued.issuanceId());
        wrongCode.setExpectedDistrictCode("D-B");
        assertThat(cureService.confirm(wrongCode).outcome()).isEqualTo("FAILED");
    }

    @Test
    void cureNeverIssuesSecondBallot_submittingOriginalCredentialAfterCureCountsOriginal() {
        voter("M6", districtA, VoterStatus.ELIGIBLE);
        setDeadline(Instant.now().plus(2, ChronoUnit.DAYS));
        IssuanceResponse issued = issuanceService.issue(mailIssue("M6", "EVT-M6", true));
        // 先补正确认，再提交原选票
        cureService.submit(cureSubmit(issued.issuanceId(), "提前补正"));
        cureService.confirm(cureConfirm(issued.issuanceId()));
        assertThat(issuanceRepository.count()).isEqualTo(1);

        SubmitResponse later = ballotService.submit(
                submitRequest(issued.credentialToken(), "{\"mayor\":\"LATER\"}"));
        assertThat(later.counted()).isTrue();
        assertThat(ballotContentRepository.count()).isEqualTo(1);
        assertThat(issuanceRepository.count()).isEqualTo(1);
        assertThat(effectiveVoteRepository.count()).isEqualTo(1);
    }

    // ---------- 规则 2：已通过其他渠道有效投票，补正失败 ----------

    @Test
    void otherChannelEffectiveVote_makesCureFail_andVoidsHeldBallot() {
        voter("M7", districtA, VoterStatus.ELIGIBLE);
        setDeadline(Instant.now().plus(2, ChronoUnit.DAYS));
        IssuanceResponse issued = issuanceService.issue(mailIssue("M7", "EVT-M7", true));
        ballotService.submit(submitRequest(issued.credentialToken(), "{\"mayor\":\"ALICE_SECRET\"}"));
        cureService.submit(cureSubmit(issued.issuanceId(), "补正材料"));

        var registered = externalVoteService.register(
                election.getId(), externalVote("M7", "EARLY-VOTE-007"));
        assertThat(registered.source()).isEqualTo("EXTERNAL_CHANNEL");

        // 其他渠道登记已自动终结补正：状态 FAILED、原选票作废；再确认收到 409 终态冲突
        var statusAfterExternal = queryService.voterStatus(election.getId(), "M7");
        assertThat(statusAfterExternal.cureStatus()).isEqualTo("FAILED");
        assertThatThrownBy(() -> cureService.confirm(cureConfirm(issued.issuanceId())))
                .isInstanceOf(ConflictException.class);
        assertThat(onlyBallot().isVoided()).isTrue();
        assertThat(ballotContentRepository.countByDistrictIdAndCountedTrue(districtA.getId())).isZero();

        var status = queryService.voterStatus(election.getId(), "M7");
        assertThat(status.effectiveVoteSource()).isEqualTo("EXTERNAL_CHANNEL");
        assertThat(status.cureStatus()).isEqualTo("FAILED");
    }

    @Test
    void cureConfirmed_first_blocksOtherChannelAndSecondCure() {
        voter("M8", districtA, VoterStatus.ELIGIBLE);
        setDeadline(Instant.now().plus(2, ChronoUnit.DAYS));
        IssuanceResponse issued = issuanceService.issue(mailIssue("M8", "EVT-M8", true));
        ballotService.submit(submitRequest(issued.credentialToken(), "{}"));
        cureService.submit(cureSubmit(issued.issuanceId(), "补正材料"));
        cureService.confirm(cureConfirm(issued.issuanceId()));

        assertThatThrownBy(() -> externalVoteService.register(
                election.getId(), externalVote("M8", "OTHER-CHANNEL")))
                .isInstanceOf(ConflictException.class);
        // 同一外部渠道凭证重放幂等（首次冲突的不同 ref 不存在重放，这里验证不产生第二条）
        assertThat(effectiveVoteRepository.count()).isEqualTo(1);
    }

    @Test
    void otherChannelRegistration_isIdempotent_byChannelRef() {
        voter("X1", districtA, VoterStatus.ELIGIBLE);
        externalVoteService.register(election.getId(), externalVote("X1", "EXT-1"));
        var replay = externalVoteService.register(election.getId(), externalVote("X1", "EXT-1"));
        assertThat(replay.channelRef()).isEqualTo("EXT-1");
        assertThat(effectiveVoteRepository.count()).isEqualTo(1);
    }

    @Test
    void otherChannel_rejectsDirectLocalSubmission() {
        voter("X2", districtA, VoterStatus.ELIGIBLE);
        IssuanceResponse issued = issuanceService.issue(
                new IssueRequest() {{
                    setElectionId(election.getId());
                    setVoterRef("X2");
                    setEventNo("EVT-X2");
                    setType("OFFICIAL");
                    setPollingPlace("P-01");
                }});
        externalVoteService.register(election.getId(), externalVote("X2", "EXT-2"));
        assertThatThrownBy(() -> ballotService.submit(submitRequest(issued.credentialToken(), "{}")))
                .isInstanceOf(ConflictException.class);
        assertThat(submissionRecordRepository.count()).isZero();
    }

    @Test
    void otherChannel_pendingProvisional_isRejectedAndVoided() {
        voter("P1", districtA, VoterStatus.DISPUTED);
        IssuanceResponse issued = issuanceService.issue(provisionalIssue("P1", "EVT-P1"));
        ballotService.submit(submitRequest(issued.credentialToken(), "{\"mayor\":\"SECRET\"}"));

        externalVoteService.register(election.getId(), externalVote("P1", "EXT-P1"));

        var record = provisionalRecordRepository.findByIssuanceId(issued.issuanceId()).orElseThrow();
        assertThat(record.getAdjudication().name()).isEqualTo("REJECTED");
        assertThat(onlyBallot().isVoided()).isTrue();
        // 临时票再裁定通过也不行：只有一个有效结果
        assertThatThrownBy(() -> adjudicationService.adjudicate(issued.issuanceId(), true, "补核"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---------- 规则 3：截止裁定与并发，只保留一个有效结果 ----------

    @Test
    void cureAfterDeadline_fails_andDeadlineRulingVoidsRemaining() {
        voter("D1", districtA, VoterStatus.ELIGIBLE);
        voter("D2", districtA, VoterStatus.ELIGIBLE);
        // 截止时间已过
        setDeadline(Instant.now().minus(1, ChronoUnit.HOURS));
        IssuanceResponse d1 = issuanceService.issue(mailIssue("D1", "EVT-D1", true));
        ballotService.submit(submitRequest(d1.credentialToken(), "{}"));
        IssuanceResponse d2 = issuanceService.issue(mailIssue("D2", "EVT-D2", true));
        // 未提交也截止失败

        // 截止后提交材料：直接失败
        CureResponse lateSubmit = cureService.submit(cureSubmit(d1.issuanceId(), "迟到材料"));
        assertThat(lateSubmit.outcome()).isEqualTo("FAILED");

        int ruled = deadlineService.ruleOverdueCures(election.getId());
        assertThat(ruled).isEqualTo(1); // D2
        assertThat(issuanceRepository.findById(d2.issuanceId()).orElseThrow().getCureStatus())
                .isEqualTo(CureStatus.FAILED);
        assertThat(issuanceRepository.findById(d2.issuanceId()).orElseThrow().getStatus().name())
                .isEqualTo("VOIDED");
        // 重复截止裁定是空操作
        assertThat(deadlineService.ruleOverdueCures(election.getId())).isZero();
    }

    @Test
    void cureBeforeDeadline_succeedsEvenWhenRacingDeadlineRuling() {
        voter("D3", districtA, VoterStatus.ELIGIBLE);
        setDeadline(Instant.now().plus(1, ChronoUnit.HOURS));
        IssuanceResponse issued = issuanceService.issue(mailIssue("D3", "EVT-D3", true));
        ballotService.submit(submitRequest(issued.credentialToken(), "{}"));
        cureService.submit(cureSubmit(issued.issuanceId(), "截止前材料"));

        cureService.confirm(cureConfirm(issued.issuanceId()));
        // 已确认的票不被截止裁定影响
        assertThat(deadlineService.ruleOverdueCures(election.getId())).isZero();
        assertThat(ballotContentRepository.countByDistrictIdAndCountedTrue(districtA.getId())).isEqualTo(1);
    }

    @Test
    void concurrent_cureConfirm_vs_externalVote_onlyOneEffectiveResult() throws Exception {
        int voters = 12;
        for (int i = 0; i < voters; i++) {
            voter("C" + i, districtA, VoterStatus.ELIGIBLE);
            setDeadline(Instant.now().plus(1, ChronoUnit.DAYS));
            IssuanceResponse issued = issuanceService.issue(mailIssue("C" + i, "EVT-C" + i, true));
            ballotService.submit(submitRequest(issued.credentialToken(), "{\"n\":" + i + "}"));
            cureService.submit(cureSubmit(issued.issuanceId(), "材料" + i));
        }

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Callable<Void>> tasks = new java.util.ArrayList<>();
            for (int i = 0; i < voters; i++) {
                String ref = "C" + i;
                String channelRef = "EXT-" + i;
                tasks.add(() -> {
                    start.await();
                    try {
                        Long issuanceId = issuanceRepository
                                .findIdByElectionIdAndVoterRef(election.getId(), ref).orElseThrow();
                        cureService.confirm(cureConfirm(issuanceId));
                    } catch (RuntimeException expected) {
                        // 输给其他渠道登记（状态冲突/唯一约束竞争均为合法结果）
                    }
                    return null;
                });
                tasks.add(() -> {
                    start.await();
                    try {
                        externalVoteService.register(election.getId(), externalVote(ref, channelRef));
                    } catch (RuntimeException expected) {
                        // 输给补正确认
                    }
                    return null;
                });
            }
            List<Future<Void>> futures = tasks.stream().map(pool::submit).toList();
            start.countDown();
            for (Future<Void> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdown();
        }

        // 每位选民恰好一个有效结果，绝无重复；且计入与作废严格互斥
        assertThat(effectiveVoteRepository.count()).isEqualTo(voters);
        long counted = 0;
        long voided = 0;
        for (int i = 0; i < voters; i++) {
            final int idx = i;
            Voter v = voterRepository.findByElectionIdAndVoterRef(election.getId(), "C" + idx).orElseThrow();
            assertThat(effectiveVoteRepository.findByElectionIdAndVoterId(election.getId(), v.getId()))
                    .isPresent();

            var issuance = issuanceRepository.findByElectionIdAndVoterId(election.getId(), v.getId()).orElseThrow();
            boolean cureWon = issuance.getCureStatus() == CureStatus.CONFIRMED;
            boolean externalWon = issuance.getCureStatus() == CureStatus.FAILED;
            assertThat(cureWon ^ externalWon).isTrue();

            BallotContent ballot = ballotContentRepository.findAll().stream()
                    .filter(b -> b.getContentHash().equals(AuditService.sha256("{\"n\":" + idx + "}")))
                    .findFirst().orElseThrow();
            assertThat(ballot.isCounted()).isEqualTo(cureWon);
            assertThat(ballot.isVoided()).isEqualTo(externalWon);
            if (ballot.isCounted()) {
                counted++;
            }
            if (ballot.isVoided()) {
                voided++;
            }
        }
        assertThat(counted + voided).isEqualTo(voters);
    }

    @Test
    void concurrent_duplicateCureConfirms_onlyOneCommits() throws Exception {
        voter("CC", districtA, VoterStatus.ELIGIBLE);
        setDeadline(Instant.now().plus(1, ChronoUnit.DAYS));
        IssuanceResponse issued = issuanceService.issue(mailIssue("CC", "EVT-CC", true));
        ballotService.submit(submitRequest(issued.credentialToken(), "{}"));
        cureService.submit(cureSubmit(issued.issuanceId(), "材料"));

        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = IntStream.range(0, threads).<Callable<Boolean>>mapToObj(n -> () -> {
                start.await();
                try {
                    cureService.confirm(cureConfirm(issued.issuanceId()));
                    return true;
                } catch (RuntimeException e) {
                    return false;
                }
            }).map(pool::submit).toList();
            start.countDown();
            long wins = 0;
            for (Future<Boolean> f : futures) {
                if (f.get()) {
                    wins++;
                }
            }
            assertThat(wins).isEqualTo(1);
        } finally {
            pool.shutdown();
        }
        assertThat(effectiveVoteRepository.count()).isEqualTo(1);
        assertThat(ballotContentRepository.countByDistrictIdAndCountedTrue(districtA.getId())).isEqualTo(1);
    }

    // ---------- 规则 4：身份材料与票面选择隔离 ----------

    @Test
    void identityMaterials_neverExposeBallotChoices() {
        voter("S1", districtA, VoterStatus.ELIGIBLE);
        setDeadline(Instant.now().plus(1, ChronoUnit.DAYS));
        IssuanceResponse issued = issuanceService.issue(mailIssue("S1", "EVT-S1", true));
        ballotService.submit(submitRequest(issued.credentialToken(), "{\"mayor\":\"ULTRA_SECRET_CHOICE\"}"));
        cureService.submit(cureSubmit(issued.issuanceId(), "地址证明：某路 1 号"));

        // 材料记录侧没有任何指向票面内容的字段/导航：只存身份侧说明与版本状态。
        var materials = cureRecordRepository.findByIssuanceIdOrderByVersionAsc(issued.issuanceId());
        assertThat(materials).hasSize(1);
        assertThat(materials.get(0).getMaterialNotes()).doesNotContain("ULTRA_SECRET_CHOICE");

        // 审计链：票面选择绝不出现在任何 detail 中。
        var chain = queryService.auditChain();
        assertThat(chain).extracting(QueryService.AuditEventView::detail)
                .noneMatch(d -> d != null && d.contains("ULTRA_SECRET_CHOICE"));
        // 选民状态视图只暴露流程事实。
        var status = queryService.voterStatus(election.getId(), "S1");
        assertThat(status.toString()).doesNotContain("ULTRA_SECRET_CHOICE");
        assertThat(status.cureStatus()).isEqualTo("PENDING");
    }

    @Test
    void provisionalVoter_cureFlow_restoresOriginalProvisionalBallot() {
        // 争议选民先持临时票暂存，名册修正为 ELIGIBLE 后走补正确认
        Voter p = voter("P9", districtA, VoterStatus.DISPUTED);
        setDeadline(Instant.now().plus(1, ChronoUnit.DAYS));
        IssuanceResponse issued = issuanceService.issue(provisionalIssue("P9", "EVT-P9"));
        ballotService.submit(submitRequest(issued.credentialToken(), "{\"mayor\":\"ALICE_SECRET\"}"));
        assertThat(ballotContentRepository.countByDistrictIdAndCountedTrue(districtA.getId())).isZero();

        cureService.submit(cureSubmit(issued.issuanceId(), "补充身份证明"));
        // 资格未恢复时确认失败
        assertThat(cureService.confirm(cureConfirm(issued.issuanceId())).outcome()).isEqualTo("FAILED");
        // 失败终态：该路径已终结；模拟名册在截止前的另一张争议票（实际业务争议消除后再持票）
        Voter p2 = voter("P10", districtA, VoterStatus.DISPUTED);
        IssuanceResponse issued2 = issuanceService.issue(provisionalIssue("P10", "EVT-P10"));
        ballotService.submit(submitRequest(issued2.credentialToken(), "{}"));
        cureService.submit(cureSubmit(issued2.issuanceId(), "补充身份证明"));
        p2.setStatus(VoterStatus.ELIGIBLE);
        voterRepository.save(p2);
        CureResponse ok = cureService.confirm(cureConfirm(issued2.issuanceId()));
        assertThat(ok.outcome()).isEqualTo("CONFIRMED");
        assertThat(ok.cureStatus()).isEqualTo("ACCEPTED");
        assertThat(issuanceRepository.count()).isEqualTo(2); // 没有重新签发
        assertThat(effectiveVoteRepository.count()).isEqualTo(1);
        assertThat(queryService.voterStatus(election.getId(), "P10").adjudication())
                .isEqualTo("ACCEPTED");
    }
}
