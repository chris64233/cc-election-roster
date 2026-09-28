package com.chris64233.electionroster.web;

import com.chris64233.electionroster.domain.BallotStyle;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ElectionApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
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
    private BallotContentRepository ballotContentRepository;
    @Autowired
    private SubmissionRecordRepository submissionRecordRepository;
    @Autowired
    private CureMaterialRepository cureMaterialRepository;
    @Autowired
    private CureRecordRepository cureRecordRepository;
    @Autowired
    private ExternalVoteRecordRepository externalVoteRecordRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;

    private Long electionId;
    private Long districtId;

    @BeforeEach
    void setUp() {
        submissionRecordRepository.deleteAllInBatch();
        externalVoteRecordRepository.deleteAllInBatch();
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

        Election election = electionRepository.save(new Election("2026 大选"));
        District district = districtRepository.save(
                new District(election, "D-A", new BallotStyle(election, "STYLE-A")));
        voterRepository.save(new Voter(election, "V-OK", district, VoterStatus.ELIGIBLE));
        voterRepository.save(new Voter(election, "V-DISPUTE", district, VoterStatus.DISPUTED));
        electionId = election.getId();
        districtId = district.getId();
    }

    private String issueBody(String ref, String eventNo, String type, String identityNotes) {
        return """
                {"electionId":%d,"voterRef":"%s","eventNo":"%s","pollingPlace":"P-01","type":"%s"%s}"""
                .formatted(electionId, ref, eventNo, type,
                        identityNotes == null ? "" : ",\"identityNotes\":\"" + identityNotes + "\"");
    }

    private String issue(String ref, String eventNo, String type, String notes) throws Exception {
        return mockMvc.perform(post("/api/issuances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(ref, eventNo, type, notes)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void issueOfficialBallot_201_andStatusQuery() throws Exception {
        String body = issue("V-OK", "EVT-1", "OFFICIAL", null);
        JsonNode json = objectMapper.readTree(body);
        String token = json.get("credentialToken").asText();
        org.assertj.core.api.Assertions.assertThat(token).isNotBlank();

        mockMvc.perform(get("/api/elections/{eid}/voters/V-OK/status", electionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issued").value(true))
                .andExpect(jsonPath("$.issuanceType").value("OFFICIAL"))
                .andExpect(jsonPath("$.voterStatus").value("ELIGIBLE"));
    }

    @Test
    void replaySameEventNo_returnsSameCredential_201() throws Exception {
        String first = issue("V-OK", "EVT-IDEMPOTENT", "OFFICIAL", null);
        String replay = issue("V-OK", "EVT-IDEMPOTENT", "OFFICIAL", null);
        org.assertj.core.api.Assertions.assertThat(objectMapper.readTree(replay).get("credentialToken").asText())
                .isEqualTo(objectMapper.readTree(first).get("credentialToken").asText());
        org.assertj.core.api.Assertions.assertThat(objectMapper.readTree(replay).get("issuanceId").asLong())
                .isEqualTo(objectMapper.readTree(first).get("issuanceId").asLong());
    }

    @Test
    void secondIssuanceForSameVoter_409() throws Exception {
        issue("V-OK", "EVT-1", "OFFICIAL", null);
        mockMvc.perform(post("/api/issuances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody("V-OK", "EVT-2", "MAIL", null)))
                .andExpect(status().isConflict());
    }

    @Test
    void officialIssuanceForDisputedVoter_409_andProvisionalWorks() throws Exception {
        mockMvc.perform(post("/api/issuances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody("V-DISPUTE", "EVT-X", "OFFICIAL", null)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/issuances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody("V-DISPUTE", "EVT-P", "PROVISIONAL", "地址待核")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("PROVISIONAL"));
    }

    @Test
    void submissionReplaySameContent_returnsOriginalReceipt_changedContent_409() throws Exception {
        String issuance = objectMapper.readTree(issue("V-OK", "EVT-1", "OFFICIAL", null))
                .get("credentialToken").asText();

        String choices = "{\"mayor\":\"ALICE_SECRET\"}";
        String first = mockMvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credentialToken\":\"%s\",\"choicesJson\":\"%s\"}"
                                .formatted(issuance, choices.replace("\"", "\\\""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(false))
                .andReturn().getResponse().getContentAsString();
        String receiptId = objectMapper.readTree(first).get("receiptId").asText();

        // 相同内容重放 -> 原回执，duplicate=true
        mockMvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credentialToken\":\"%s\",\"choicesJson\":\"%s\"}"
                                .formatted(issuance, choices.replace("\"", "\\\""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.receiptId").value(receiptId));

        // 内容变化 -> 409
        mockMvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credentialToken\":\"%s\",\"choicesJson\":\"%s\"}"
                                .formatted(issuance, "{\\\"mayor\\\":\\\"BOB_SECRET\\\"}")))
                .andExpect(status().isConflict());
    }

    @Test
    void provisionalAcceptedThenCounted_summaryAndAuditReflectIt() throws Exception {
        long issuanceId = objectMapper.readTree(
                        issue("V-DISPUTE", "EVT-P", "PROVISIONAL", "地址待核"))
                .get("issuanceId").asLong();
        String token = issuanceRepository.findById(issuanceId).orElseThrow().getCredentialToken();

        mockMvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credentialToken\":\"%s\",\"choicesJson\":\"{}\"}".formatted(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counted").value(false));

        mockMvc.perform(get("/api/districts/{id}/summary", districtId))
                .andExpect(jsonPath("$.countedBallots").value(0))
                .andExpect(jsonPath("$.pendingProvisionals").value(1));

        mockMvc.perform(post("/api/provisionals/{id}/adjudication", issuanceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":true,\"reason\":\"核验通过\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adjudication").value("ACCEPTED"));

        mockMvc.perform(get("/api/districts/{id}/summary", districtId))
                .andExpect(jsonPath("$.countedBallots").value(1))
                .andExpect(jsonPath("$.pendingProvisionals").value(0));

        // 重复裁定 -> 409
        mockMvc.perform(post("/api/provisionals/{id}/adjudication", issuanceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":false}"))
                .andExpect(status().isConflict());

        // 审计链：追加式、事件类型可查；选择内容不出现在任何 detail 中
        String audit = mockMvc.perform(get("/api/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("ISSUE"))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(audit).doesNotContain("SECRET");
    }

    private void setFutureCureDeadline() {
        electionRepository.findById(electionId).ifPresent(e -> {
            e.setCureDeadline(java.time.Instant.now().plus(java.time.Duration.ofDays(7)));
            electionRepository.save(e);
        });
    }

    @Test
    void mailBallotHeldForCure_thenMaterialAndConfirm_restoresIt() throws Exception {
        setFutureCureDeadline();
        long issuanceId = objectMapper.readTree(
                        issue("V-OK", "EVT-MAIL-CURE", "MAIL", null))
                .get("issuanceId").asLong();
        String token = issuanceRepository.findById(issuanceId).orElseThrow().getCredentialToken();

        // 提交时声明身份材料不全 -> held=true、不计入
        mockMvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credentialToken\":\"%s\",\"choicesJson\":\"{\\\"mayor\\\":\\\"ALICE_SECRET\\\"}\","
                                .formatted(token)
                                + "\"identityIncomplete\":true,\"missingMaterials\":\"缺证件\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counted").value(false))
                .andExpect(jsonPath("$.held").value(true));

        mockMvc.perform(get("/api/districts/{id}/summary", districtId))
                .andExpect(jsonPath("$.countedBallots").value(0))
                .andExpect(jsonPath("$.heldBallots").value(1))
                .andExpect(jsonPath("$.pendingCures").value(1));

        // 截止前提交新材料：新版本，仍关联原选票
        mockMvc.perform(post("/api/issuances/{id}/cure/materials", issuanceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialNotes\":\"补交护照\",\"materialContent\":\"DOC-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.cureStatus").value("PENDING"));

        // 确认恢复原选票计入
        mockMvc.perform(post("/api/issuances/{id}/cure/confirm", issuanceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"核验通过\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restored").value(true))
                .andExpect(jsonPath("$.cureStatus").value("CONFIRMED"));

        mockMvc.perform(get("/api/districts/{id}/summary", districtId))
                .andExpect(jsonPath("$.countedBallots").value(1))
                .andExpect(jsonPath("$.heldBallots").value(0))
                .andExpect(jsonPath("$.pendingCures").value(0));

        // 选民状态暴露 cureStatus 但不暴露材料/票面；审计链不出现选择内容与材料正文
        mockMvc.perform(get("/api/elections/{eid}/voters/V-OK/status", electionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cureStatus").value("CONFIRMED"));
        String audit = mockMvc.perform(get("/api/audit")).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(audit).doesNotContain("ALICE_SECRET", "DOC-1");
    }

    @Test
    void externalVote_supersedesHeldBallot_andConfirmFails_409() throws Exception {
        setFutureCureDeadline();
        long issuanceId = objectMapper.readTree(
                        issue("V-OK", "EVT-MAIL-EXT", "MAIL", null))
                .get("issuanceId").asLong();
        String token = issuanceRepository.findById(issuanceId).orElseThrow().getCredentialToken();

        mockMvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credentialToken\":\"%s\",\"choicesJson\":\"{}\","
                                .formatted(token) + "\"identityIncomplete\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/issuances/{id}/cure/materials", issuanceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialNotes\":\"补交证件\"}"))
                .andExpect(status().isOk());

        // 其他渠道有效投票：本地暂存选票作废
        mockMvc.perform(post("/api/elections/{eid}/external-votes", electionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voterRef\":\"V-OK\",\"channel\":\"OTHER_PLACE\",\"externalRef\":\"EXT-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("LOCAL_BALLOT_VOIDED"));

        // 再确认补正 -> 409，选票未计入
        mockMvc.perform(post("/api/issuances/{id}/cure/confirm", issuanceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/districts/{id}/summary", districtId))
                .andExpect(jsonPath("$.countedBallots").value(0));

        // 同一外部凭证重放幂等
        mockMvc.perform(post("/api/elections/{eid}/external-votes", electionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voterRef\":\"V-OK\",\"channel\":\"OTHER_PLACE\",\"externalRef\":\"EXT-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DUPLICATE"));
    }
}
