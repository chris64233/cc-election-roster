package com.chris64233.electionroster;

import com.chris64233.electionroster.domain.AuditEntry;
import com.chris64233.electionroster.domain.Election;
import com.chris64233.electionroster.domain.VoterStatus;
import com.chris64233.electionroster.repository.AuditEntryRepository;
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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AuditAndQueryTest extends ElectionTestSupport {

    @Autowired
    private IssuanceService issuanceService;
    @Autowired
    private BallotSubmissionService submissionService;
    @Autowired
    private AdjudicationService adjudicationService;
    @Autowired
    private QueryService queryService;
    @Autowired
    private AuditEntryRepository auditEntryRepository;

    @Test
    void auditChainIsLinkedAndDoesNotExposeChoices() {
        Election election = newElection();
        setupBallotStructure(election.getId());
        registerVoter(election.getId(), "V201", VoterStatus.ELIGIBLE);
        registerVoter(election.getId(), "V202", VoterStatus.DISPUTED);

        IssuanceResult official = issuanceService.issue(election.getId(),
                new IssueCommand("V201", "evt-a1", null, "P1", "投票站A", null, null, null));
        IssuanceResult provisional = issuanceService.issue(election.getId(),
                new IssueCommand("V202", "evt-a2", null, "P1", "投票站B", "身份证", "officer-9", null));

        String secretChoice = "候选人=绝密选择";
        submissionService.submit(official.credentialToken(), secretChoice);
        SubmissionResult provSubmitted = submissionService.submit(provisional.credentialToken(), secretChoice);
        adjudicationService.adjudicate(provSubmitted.provisionalBallotId(), true, "资格确认");

        List<AuditEntry> entries = auditEntryRepository.findByElectionIdOrderById(election.getId());
        assertThat(entries).hasSizeGreaterThanOrEqualTo(4);

        // 哈希链完整：每条目的 prevHash 等于前一条目的 entryHash
        for (int i = 1; i < entries.size(); i++) {
            assertThat(entries.get(i).getPrevHash()).isEqualTo(entries.get(i - 1).getEntryHash());
        }
        // 审计链不暴露票面选择内容
        for (AuditEntry entry : entries) {
            assertThat(entry.getDetail()).doesNotContain(secretChoice);
            assertThat(entry.getRefToken()).doesNotContain(secretChoice);
        }
    }

    @Test
    void voterIssuanceStatusReflectsLifecycle() {
        Election election = newElection();
        setupBallotStructure(election.getId());
        registerVoter(election.getId(), "V203", VoterStatus.ELIGIBLE);

        QueryService.VoterIssuanceStatusView before =
                queryService.voterIssuanceStatus(election.getId(), "V203");
        assertThat(before.issued()).isFalse();
        assertThat(before.rosterStatus()).isEqualTo("ELIGIBLE");

        IssuanceResult issuance = issuanceService.issue(election.getId(),
                new IssueCommand("V203", "evt-q1", null, "P1", "投票站A", null, null, null));
        QueryService.VoterIssuanceStatusView issued =
                queryService.voterIssuanceStatus(election.getId(), "V203");
        assertThat(issued.issued()).isTrue();
        assertThat(issued.issuanceType()).isEqualTo("OFFICIAL");
        assertThat(issued.issuanceStatus()).isEqualTo("ISSUED");

        submissionService.submit(issuance.credentialToken(), "候选人=甲");
        QueryService.VoterIssuanceStatusView consumed =
                queryService.voterIssuanceStatus(election.getId(), "V203");
        assertThat(consumed.issuanceStatus()).isEqualTo("CONSUMED");
        assertThat(consumed.consumedAt()).isNotNull();
    }

    @Test
    void provisionalQueryHidesContentAndIdentity() {
        Election election = newElection();
        setupBallotStructure(election.getId());
        registerVoter(election.getId(), "V204", VoterStatus.DISPUTED);
        IssuanceResult issuance = issuanceService.issue(election.getId(),
                new IssueCommand("V204", "evt-q2", null, "P1", "投票站B", "身份证", "officer-5", null));
        submissionService.submit(issuance.credentialToken(), "候选人=匿名");

        List<QueryService.ProvisionalBallotView> pending =
                queryService.provisionalBallots(election.getId(),
                        com.chris64233.electionroster.domain.AdjudicationStatus.PENDING);
        assertThat(pending).hasSize(1);
        QueryService.ProvisionalBallotView view = pending.get(0);
        assertThat(view.adjudicationStatus()).isEqualTo("PENDING");
        assertThat(view.precinctCode()).isEqualTo("P1");
        // 视图不含票面内容与选民身份字段
        assertThat(view.toString()).doesNotContain("候选人").doesNotContain("V204");
    }

    @Test
    void concurrentSubmissionOfSameCredentialConsumesOnlyOnce() throws Exception {
        Election election = newElection();
        setupBallotStructure(election.getId());
        registerVoter(election.getId(), "V205", VoterStatus.ELIGIBLE);
        IssuanceResult issuance = issuanceService.issue(election.getId(),
                new IssueCommand("V205", "evt-c1", null, "P1", "投票站A", null, null, null));
        String token = issuance.credentialToken();

        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    return submissionService.submit(token, "候选人=并发");
                } catch (ConflictException e) {
                    return e;
                }
            }));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();

        int consumed = 0;
        for (Future<Object> future : futures) {
            Object outcome = future.get(30, TimeUnit.SECONDS);
            if (outcome instanceof SubmissionResult) {
                consumed++;
            }
        }
        pool.shutdown();

        // 所有并发提交内容相同：一个真实消费，其余幂等重放；选区只计入一票
        assertThat(consumed).isEqualTo(threads);
        assertThat(queryService.precinctSummary(election.getId(), "P1").countedTotal()).isEqualTo(1);
    }
}
