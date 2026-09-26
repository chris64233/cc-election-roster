package com.chris64233.electionroster.web;

import com.chris64233.electionroster.api.AdjudicateRequest;
import com.chris64233.electionroster.api.AdjudicationResponse;
import com.chris64233.electionroster.api.IssueRequest;
import com.chris64233.electionroster.api.IssuanceResponse;
import com.chris64233.electionroster.api.SubmitRequest;
import com.chris64233.electionroster.api.SubmitResponse;
import com.chris64233.electionroster.service.AdjudicationService;
import com.chris64233.electionroster.service.BallotService;
import com.chris64233.electionroster.service.IssuanceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ElectionController {

    private final IssuanceService issuanceService;
    private final BallotService ballotService;
    private final AdjudicationService adjudicationService;

    public ElectionController(IssuanceService issuanceService,
                              BallotService ballotService,
                              AdjudicationService adjudicationService) {
        this.issuanceService = issuanceService;
        this.ballotService = ballotService;
        this.adjudicationService = adjudicationService;
    }

    /** 签发正式票 / 临时票，或登记邮寄票（按 type 区分，共享同一唯一性约束）。 */
    @PostMapping("/issuances")
    @ResponseStatus(HttpStatus.CREATED)
    public IssuanceResponse issue(@Valid @RequestBody IssueRequest request) {
        return issuanceService.issue(request);
    }

    /** 提交选票：凭证消费一次；同内容重放返回原回执，内容变化返回 409。 */
    @PostMapping("/submissions")
    public SubmitResponse submit(@Valid @RequestBody SubmitRequest request) {
        return ballotService.submit(request);
    }

    /** 裁定临时票：accepted=true 计入选区，false 永久作废。 */
    @PostMapping("/provisionals/{issuanceId}/adjudication")
    public AdjudicationResponse adjudicate(@PathVariable Long issuanceId,
                                           @Valid @RequestBody AdjudicateRequest request) {
        return adjudicationService.adjudicate(issuanceId, request.getAccepted(), request.getReason());
    }
}
