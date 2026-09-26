package com.chris64233.electionroster;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 端到端 Web 层测试：登记 → 签发 → 提交 → 裁定 → 查询。 */
@AutoConfigureMockMvc
class WebApiTest extends ElectionTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void fullLifecycleOverHttp() throws Exception {
        Long electionId = newElection().getId();
        String base = "/api/elections/" + electionId;

        mockMvc.perform(post(base + "/precincts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"P1\",\"name\":\"第一选区\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post(base + "/ballot-styles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"S1\",\"description\":\"样式一\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post(base + "/voters").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voterRef\":\"V301\",\"precinctCode\":\"P1\",\"ballotStyleCode\":\"S1\",\"status\":\"ELIGIBLE\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post(base + "/voters").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voterRef\":\"V302\",\"precinctCode\":\"P1\",\"ballotStyleCode\":\"S1\",\"status\":\"DISPUTED\"}"))
                .andExpect(status().isCreated());

        // 签发正式票（幂等：同一事件号重复请求返回原凭证）
        String issueBody = "{\"voterRef\":\"V301\",\"eventId\":\"evt-http-1\",\"precinctCode\":\"P1\",\"pollingPlace\":\"投票站A\"}";
        MvcResult issued = mockMvc.perform(post(base + "/issuances").contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("OFFICIAL"))
                .andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(
                issued.getResponse().getContentAsString(), "$.credentialToken");
        MvcResult replayed = mockMvc.perform(post(base + "/issuances").contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true))
                .andReturn();
        assertThat(com.jayway.jsonpath.JsonPath.<String>read(
                replayed.getResponse().getContentAsString(), "$.credentialToken")).isEqualTo(token);

        // 提交正式票
        mockMvc.perform(post(base + "/submissions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credentialToken\":\"" + token + "\",\"choicePayload\":\"候选人=甲\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("COUNTED"));
        // 重复提交相同内容 → 200 原结果；内容变化 → 409
        mockMvc.perform(post(base + "/submissions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credentialToken\":\"" + token + "\",\"choicePayload\":\"候选人=甲\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true));
        mockMvc.perform(post(base + "/submissions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credentialToken\":\"" + token + "\",\"choicePayload\":\"候选人=乙\"}"))
                .andExpect(status().isConflict());

        // 争议选民签发临时票并提交
        MvcResult provIssued = mockMvc.perform(post(base + "/issuances").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voterRef\":\"V302\",\"eventId\":\"evt-http-2\",\"precinctCode\":\"P1\","
                                + "\"pollingPlace\":\"投票站B\",\"verificationMethod\":\"身份证\",\"verifierRef\":\"officer-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("PROVISIONAL"))
                .andReturn();
        String provToken = com.jayway.jsonpath.JsonPath.read(
                provIssued.getResponse().getContentAsString(), "$.credentialToken");
        MvcResult provSubmitted = mockMvc.perform(post(base + "/submissions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credentialToken\":\"" + provToken + "\",\"choicePayload\":\"候选人=丙\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("PROVISIONAL_PENDING"))
                .andReturn();
        Integer provisionalId = com.jayway.jsonpath.JsonPath.read(
                provSubmitted.getResponse().getContentAsString(), "$.provisionalBallotId");

        // 裁定通过 → 计入选区
        mockMvc.perform(post(base + "/provisional-ballots/" + provisionalId + "/adjudication")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accept\":true,\"reason\":\"资格确认\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // 查询：选民签发状态、临时票列表、选区汇总
        mockMvc.perform(get(base + "/voters/V301/issuance-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issued").value(true))
                .andExpect(jsonPath("$.issuanceStatus").value("CONSUMED"));
        mockMvc.perform(get(base + "/provisional-ballots").param("status", "ACCEPTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].adjudicationStatus").value("ACCEPTED"));
        MvcResult summary = mockMvc.perform(get(base + "/precincts/P1/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countedOfficial").value(1))
                .andExpect(jsonPath("$.countedProvisional").value(1))
                .andExpect(jsonPath("$.countedTotal").value(2))
                .andReturn();
        // 汇总响应不泄露票面选择与选民身份
        assertThat(summary.getResponse().getContentAsString())
                .doesNotContain("候选人").doesNotContain("V301").doesNotContain("V302");
    }

    @Test
    void unknownVoterIssuanceReturns404() throws Exception {
        Long electionId = newElection().getId();
        mockMvc.perform(post("/api/elections/" + electionId + "/issuances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voterRef\":\"ghost\",\"eventId\":\"evt-" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
    }
}
