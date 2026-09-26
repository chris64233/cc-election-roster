package com.chris64233.electionroster;

import com.chris64233.electionroster.domain.Election;
import com.chris64233.electionroster.domain.IssuanceType;
import com.chris64233.electionroster.domain.VoterStatus;
import com.chris64233.electionroster.service.ConflictException;
import com.chris64233.electionroster.service.IssuanceResult;
import com.chris64233.electionroster.service.IssuanceService;
import com.chris64233.electionroster.service.IssuanceService.IssueCommand;
import com.chris64233.electionroster.service.UnprocessableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IssuanceServiceTest extends ElectionTestSupport {

    @Autowired
    private IssuanceService issuanceService;

    @Test
    void eligibleVoterGetsOfficialBallot() {
        Election election = newElection();
        setupBallotStructure(election.getId());
        registerVoter(election.getId(), "V001", VoterStatus.ELIGIBLE);

        IssuanceResult result = issuanceService.issue(election.getId(),
                new IssueCommand("V001", "evt-1", null, "P1", "投票站A", null, null, null));

        assertThat(result.type()).isEqualTo(IssuanceType.OFFICIAL);
        assertThat(result.status()).isEqualTo("ISSUED");
        assertThat(result.precinctCode()).isEqualTo("P1");
        assertThat(result.ballotStyleCode()).isEqualTo("S1");
        assertThat(result.credentialToken()).isNotBlank();
        assertThat(result.replayed()).isFalse();
    }

    @Test
    void eligibleVoterCanRegisterMailBallot() {
        Election election = newElection();
        setupBallotStructure(election.getId());
        registerVoter(election.getId(), "V002", VoterStatus.ELIGIBLE);

        IssuanceResult result = issuanceService.issue(election.getId(),
                new IssueCommand("V002", "evt-mail", IssuanceType.MAIL, "P1", null, null, null, null));

        assertThat(result.type()).isEqualTo(IssuanceType.MAIL);
    }

    @Test
    void disputedVoterOnlyGetsProvisionalWithVerification() {
        Election election = newElection();
        setupBallotStructure(election.getId());
        registerVoter(election.getId(), "V003", VoterStatus.DISPUTED);

        // 即使请求正式票，争议选民也只能取得临时票
        IssuanceResult result = issuanceService.issue(election.getId(),
                new IssueCommand("V003", "evt-prov", IssuanceType.OFFICIAL, "P1", "投票站B",
                        "身份证+辅助证明", "officer-7", "住址待核实"));
        assertThat(result.type()).isEqualTo(IssuanceType.PROVISIONAL);

        // 缺少身份核验信息时拒绝签发
        registerVoter(election.getId(), "V004", VoterStatus.DISPUTED);
        assertThatThrownBy(() -> issuanceService.issue(election.getId(),
                new IssueCommand("V004", "evt-prov-2", null, "P1", "投票站B", null, null, null)))
                .isInstanceOf(UnprocessableException.class)
                .hasMessageContaining("身份核验");
    }

    @Test
    void ineligibleVoterCannotBeIssued() {
        Election election = newElection();
        setupBallotStructure(election.getId());
        registerVoter(election.getId(), "V005", VoterStatus.INELIGIBLE);

        assertThatThrownBy(() -> issuanceService.issue(election.getId(),
                new IssueCommand("V005", "evt-x", null, "P1", "投票站A", null, null, null)))
                .isInstanceOf(UnprocessableException.class);
    }

    @Test
    void precinctMismatchIsRejected() {
        Election election = newElection();
        setupBallotStructure(election.getId());
        registerVoter(election.getId(), "V006", VoterStatus.ELIGIBLE);

        assertThatThrownBy(() -> issuanceService.issue(election.getId(),
                new IssueCommand("V006", "evt-y", null, "P2", "投票站A", null, null, null)))
                .isInstanceOf(UnprocessableException.class)
                .hasMessageContaining("选区");
    }

    @Test
    void sameEventIdReturnsOriginalIssuance() {
        Election election = newElection();
        setupBallotStructure(election.getId());
        registerVoter(election.getId(), "V007", VoterStatus.ELIGIBLE);

        IssueCommand command = new IssueCommand("V007", "evt-dup", null, "P1", "投票站A", null, null, null);
        IssuanceResult first = issuanceService.issue(election.getId(), command);
        IssuanceResult second = issuanceService.issue(election.getId(), command);

        assertThat(second.replayed()).isTrue();
        assertThat(second.credentialToken()).isEqualTo(first.credentialToken());
        assertThat(second.issuanceId()).isEqualTo(first.issuanceId());
    }

    @Test
    void officialProvisionalAndMailShareUniqueness() {
        Election election = newElection();
        setupBallotStructure(election.getId());
        registerVoter(election.getId(), "V008", VoterStatus.ELIGIBLE);

        issuanceService.issue(election.getId(),
                new IssueCommand("V008", "evt-mail-1", IssuanceType.MAIL, "P1", null, null, null, null));

        // 已登记邮寄票后，再到投票站签发正式票（不同事件号）必须冲突
        assertThatThrownBy(() -> issuanceService.issue(election.getId(),
                new IssueCommand("V008", "evt-official-1", IssuanceType.OFFICIAL, "P1", "投票站A",
                        null, null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void concurrentIssuanceAtDifferentPollingPlacesAllowsOnlyOne() throws Exception {
        Election election = newElection();
        setupBallotStructure(election.getId());
        registerVoter(election.getId(), "V009", VoterStatus.ELIGIBLE);
        Long electionId = election.getId();

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int n = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    return issuanceService.issue(electionId, new IssueCommand(
                            "V009", "evt-concurrent-" + n, null, "P1", "投票站-" + n, null, null, null));
                } catch (ConflictException e) {
                    return e;
                }
            }));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();

        int successes = 0;
        int conflicts = 0;
        for (Future<Object> future : futures) {
            Object outcome = future.get(30, TimeUnit.SECONDS);
            if (outcome instanceof IssuanceResult) {
                successes++;
            } else if (outcome instanceof ConflictException) {
                conflicts++;
            }
        }
        pool.shutdown();

        assertThat(successes).isEqualTo(1);
        assertThat(conflicts).isEqualTo(threads - 1);
    }
}
