package com.chris64233.electionroster.web;

import com.chris64233.electionroster.service.BallotSubmissionService;
import com.chris64233.electionroster.service.IssuanceResult;
import com.chris64233.electionroster.service.IssuanceService;
import com.chris64233.electionroster.service.SubmissionResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 选票签发与投票提交端点。 */
@RestController
@RequestMapping("/api/elections/{electionId}")
public class IssuanceController {

    private final IssuanceService issuanceService;
    private final BallotSubmissionService submissionService;

    public IssuanceController(IssuanceService issuanceService,
                              BallotSubmissionService submissionService) {
        this.issuanceService = issuanceService;
        this.submissionService = submissionService;
    }

    /** 签发选票（正式/临时/邮寄登记）。同一事件号重复请求返回原签发结果。 */
    @PostMapping("/issuances")
    ResponseEntity<IssuanceResult> issue(@PathVariable Long electionId,
                                         @Valid @RequestBody Requests.IssueBallot request) {
        IssuanceResult result = issuanceService.issue(electionId, new IssuanceService.IssueCommand(
                request.voterRef(), request.eventId(), request.requestedType(),
                request.precinctCode(), request.pollingPlace(),
                request.verificationMethod(), request.verifierRef(), request.verificationNotes()));
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
    }

    /** 提交选票，消费签发凭证。重复提交相同内容返回原结果，内容变化返回 409。 */
    @PostMapping("/submissions")
    ResponseEntity<SubmissionResult> submit(@PathVariable Long electionId,
                                            @Valid @RequestBody Requests.SubmitBallot request) {
        SubmissionResult result = submissionService.submit(request.credentialToken(), request.choicePayload());
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
    }
}
