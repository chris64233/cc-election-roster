package com.chris64233.electionroster.web;

import com.chris64233.electionroster.api.CureConfirmRequest;
import com.chris64233.electionroster.api.CureResponse;
import com.chris64233.electionroster.api.CureSubmitRequest;
import com.chris64233.electionroster.api.ExternalVoteRequest;
import com.chris64233.electionroster.api.ExternalVoteResponse;
import com.chris64233.electionroster.service.CureService;
import com.chris64233.electionroster.service.DeadlineService;
import com.chris64233.electionroster.service.ExternalVoteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 补正与跨渠道投票接口。
 * 补正只恢复原选票：提交材料与确认均以原 issuanceId 关联，不签发新凭证。
 */
@RestController
@RequestMapping("/api")
public class CureController {

    private final CureService cureService;
    private final DeadlineService deadlineService;
    private final ExternalVoteService externalVoteService;

    public CureController(CureService cureService,
                          DeadlineService deadlineService,
                          ExternalVoteService externalVoteService) {
        this.cureService = cureService;
        this.deadlineService = deadlineService;
        this.externalVoteService = externalVoteService;
    }

    /** 截止前提交补正材料：产生关联原选票的材料新版本，旧待确认版本被替代。 */
    @PostMapping("/cures/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    public CureResponse submitCure(@Valid @RequestBody CureSubmitRequest request) {
        return cureService.submit(request);
    }

    /** 确认补正：重新核验截止时间、选区与选民状态，且不得已通过其他渠道投票。 */
    @PostMapping("/cures/confirmations")
    public CureResponse confirmCure(@Valid @RequestBody CureConfirmRequest request) {
        return cureService.confirm(request);
    }

    /** 截止裁定：将选举内所有仍未完成补正的暂存选票裁定失败、永久作废。 */
    @PostMapping("/elections/{electionId}/deadline-ruling")
    public Map<String, Object> deadlineRuling(@PathVariable Long electionId) {
        int ruled = deadlineService.ruleOverdueCures(electionId);
        return Map.of("electionId", electionId, "ruled", ruled);
    }

    /** 登记“已通过其他渠道有效投票”：与补正确认/裁定竞争唯一有效结果槽位。 */
    @PostMapping("/elections/{electionId}/external-votes")
    @ResponseStatus(HttpStatus.CREATED)
    public ExternalVoteResponse registerExternalVote(@PathVariable Long electionId,
                                                     @Valid @RequestBody ExternalVoteRequest request) {
        return externalVoteService.register(electionId, request);
    }
}
