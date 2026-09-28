package com.chris64233.electionroster.web;

import com.chris64233.electionroster.domain.BallotStyle;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BallotCureApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
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

    private Long electionId;
    private Long districtId;

    @BeforeEach
    void setUp() {
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

        Election election = electionRepository.save(new Election("2026 补正选举"));
        District district = districtRepository.save(
                new District(election, "D-A", new BallotStyle(election, "STYLE-A")));
        voterRepository.save(new Voter(election, "V-MAIL", district, VoterStatus.ELIGIBLE));
        electionId = election.getId();
        districtId = district.getId();
    }

    private long issueHeldMail(String voterRef, String eventNo) throws Exception {
        String body = """
                {"electionId":%d,"voterRef":"%s","eventNo":"%s","pollingPlace":"MAIL-OFFICE",
                 "type":"MAIL","identityIncomplete":true}"""
                .formatted(electionId, voterRef, eventNo);
        String resp = mockMvc.perform(post("/api/issuances")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("MAIL"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("issuanceId").asLong();
    }

    private String tokenOf(long issuanceId) {
        return issuanceRepository.findById(issuanceId).orElseThrow().getCredentialToken();
    }

    @Test
    void heldMailBallot_cureSubmitAndConfirm_restoresOriginal_noSecondBallot() throws Exception {
        long issuanceId = issueHeldMail("V-MAIL", "EVT-M1");

        mockMvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"credentialToken":"%s","choicesJson":"{\\"mayor\\":\\"ALICE_SECRET\\"}"}"""
                                .formatted(tokenOf(issuanceId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counted").value(false));

        mockMvc.perform(get("/api/districts/{id}/summary", districtId))
                .andExpect(jsonPath("$.countedBallots").value(0))
                .andExpect(jsonPath("$.pendingCures").value(1));

        // 提交补正材料 v1
        mockMvc.perform(post("/api/cures/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"issuanceId":%d,"materialNotes":"新版驾照与地址账单"}"""
                                .formatted(issuanceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.outcome").value("SUBMITTED"))
                .andExpect(jsonPath("$.cureStatus").value("PENDING"));

        // 确认补正
        mockMvc.perform(post("/api/cures/confirmations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"issuanceId":%d,"expectedDistrictCode":"D-A"}"""
                                .formatted(issuanceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("CONFIRMED"))
                .andExpect(jsonPath("$.cureStatus").value("CONFIRMED"));

        mockMvc.perform(get("/api/districts/{id}/summary", districtId))
                .andExpect(jsonPath("$.countedBallots").value(1))
                .andExpect(jsonPath("$.pendingCures").value(0));

        // 状态接口：唯一有效结果来自补正确认；不暴露票面选择
        mockMvc.perform(get("/api/elections/{eid}/voters/V-MAIL/status", electionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cureStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.effectiveVoteSource").value("CURE_CONFIRMED"))
                .andExpect(jsonPath("$.issuanceStatus").value("CONSUMED"));

        org.assertj.core.api.Assertions.assertThat(issuanceRepository.count()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(ballotContentRepository.count()).isEqualTo(1);
        mockMvc.perform(get("/api/audit"))
                .andDo(r -> org.assertj.core.api.Assertions
                        .assertThat(r.getResponse().getContentAsString())
                        .doesNotContain("ALICE_SECRET"));
    }

    @Test
    void otherChannelVote_first_thenCureConfirm_conflicts() throws Exception {
        long issuanceId = issueHeldMail("V-MAIL", "EVT-M2");
        mockMvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"credentialToken":"%s","choicesJson":"{}"}"""
                                .formatted(tokenOf(issuanceId))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/cures/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"issuanceId":%d,"materialNotes":"材料"}""".formatted(issuanceId)))
                .andExpect(status().isCreated());

        // 其他渠道先有效投票
        mockMvc.perform(post("/api/elections/{eid}/external-votes", electionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"voterRef":"V-MAIL","channelRef":"EARLY-001"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("EXTERNAL_CHANNEL"));

        // 补正已被自动终结，确认 409
        mockMvc.perform(post("/api/cures/confirmations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"issuanceId":%d}""".formatted(issuanceId)))
                .andExpect(status().isConflict());

        // 同渠道凭证重放幂等
        mockMvc.perform(post("/api/elections/{eid}/external-votes", electionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"voterRef":"V-MAIL","channelRef":"EARLY-001"}"""))
                .andExpect(status().isCreated());
        // 不同渠道再投 409
        mockMvc.perform(post("/api/elections/{eid}/external-votes", electionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"voterRef":"V-MAIL","channelRef":"ELSEWHERE-9"}"""))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/districts/{id}/summary", districtId))
                .andExpect(jsonPath("$.countedBallots").value(0));
    }

    @Test
    void deadlineRuling_afterDeadline_voidsHeldBallots() throws Exception {
        long issuanceId = issueHeldMail("V-MAIL", "EVT-M3");
        mockMvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"credentialToken":"%s","choicesJson":"{}"}"""
                                .formatted(tokenOf(issuanceId))))
                .andExpect(status().isOk());

        // 设置一个已过的截止时间并执行截止裁定
        Election election = electionRepository.findById(electionId).orElseThrow();
        election.setCureDeadline(Instant.now().minus(1, ChronoUnit.HOURS));
        electionRepository.save(election);

        mockMvc.perform(post("/api/elections/{eid}/deadline-ruling", electionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruled").value(1));

        mockMvc.perform(get("/api/elections/{eid}/voters/V-MAIL/status", electionId))
                .andExpect(jsonPath("$.cureStatus").value("FAILED"));
        mockMvc.perform(get("/api/districts/{id}/summary", districtId))
                .andExpect(jsonPath("$.countedBallots").value(0))
                .andExpect(jsonPath("$.pendingCures").value(0));

        // 截止后再提交补正材料：终态冲突
        mockMvc.perform(post("/api/cures/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"issuanceId":%d,"materialNotes":"迟到材料"}""".formatted(issuanceId)))
                .andExpect(status().isConflict());
    }

    @Test
    void admin_setsDeadline_andUpdatesRoster() throws Exception {
        String deadline = Instant.now().plus(2, ChronoUnit.DAYS).toString();
        mockMvc.perform(post("/api/admin/elections/{eid}/cure-deadline", electionId)
                        .param("deadline", deadline))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cureDeadline").exists());

        mockMvc.perform(post("/api/admin/elections/{eid}/voters/V-MAIL/roster", electionId)
                        .param("status", "INELIGIBLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voterStatus").value("INELIGIBLE"));
    }
}
