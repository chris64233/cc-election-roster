package com.chris64233.electionroster.web;

import com.chris64233.electionroster.api.CureConfirmationResponse;
import com.chris64233.electionroster.api.CureConfirmRequest;
import com.chris64233.electionroster.api.CureMaterialRequest;
import com.chris64233.electionroster.api.CureMaterialResponse;
import com.chris64233.electionroster.api.ExternalVoteRequest;
import com.chris64233.electionroster.api.ExternalVoteResponse;
import com.chris64233.electionroster.service.CureService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 身份材料补正与跨渠道投票接口。
 * 补正只恢复原选票、不重新签发；确认/外部投票/截止裁定并发时只保留一个有效结果。
 */
@RestController
@RequestMapping("/api")
public class CureController {

    private final CureService cureService;

    public CureController(CureService cureService) {
        this.cureService = cureService;
    }

    /** 截止前提交补正新材料：形成新版本，关联原选票（原签发），不重新签发。 */
    @PostMapping("/issuances/{issuanceId}/cure/materials")
    public CureMaterialResponse submitMaterial(@PathVariable Long issuanceId,
                                               @Valid @RequestBody CureMaterialRequest request) {
        return cureService.submitMaterial(issuanceId,
                request.getMaterialNotes(), request.getMaterialContent());
    }

    /** 补正确认：复核截止时间、选民状态、选区与其他渠道投票后，恢复原选票计入。 */
    @PostMapping("/issuances/{issuanceId}/cure/confirm")
    public CureConfirmationResponse confirm(@PathVariable Long issuanceId,
                                            @RequestBody(required = false) CureConfirmRequest request) {
        String reason = request != null ? request.getReason() : null;
        return cureService.confirm(issuanceId, reason);
    }

    /** 登记选民已通过其他渠道有效投票（跨渠道凭证消费原子完成）。 */
    @PostMapping("/elections/{electionId}/external-votes")
    public ExternalVoteResponse recordExternalVote(@PathVariable Long electionId,
                                                   @Valid @RequestBody ExternalVoteRequest request) {
        return cureService.recordExternalVote(electionId, request.getVoterRef(),
                request.getChannel(), request.getExternalRef());
    }

    /** 截止裁定：把逾期仍待补正的选票作废。 */
    @PostMapping("/cures/expire")
    public java.util.Map<String, Object> expireOverdue() {
        return java.util.Map.of("decided", cureService.expireOverdueCures());
    }
}
