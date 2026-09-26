package com.chris64233.electionroster.web;

import com.chris64233.electionroster.domain.AdjudicationStatus;
import com.chris64233.electionroster.service.AdjudicationService;
import com.chris64233.electionroster.service.QueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 临时票裁定与查询端点。 */
@RestController
@RequestMapping("/api/elections/{electionId}")
public class AdjudicationController {

    private final AdjudicationService adjudicationService;
    private final QueryService queryService;

    public AdjudicationController(AdjudicationService adjudicationService, QueryService queryService) {
        this.adjudicationService = adjudicationService;
        this.queryService = queryService;
    }

    /** 裁定临时票：通过则计入选区，拒绝则永久作废。 */
    @PostMapping("/provisional-ballots/{provisionalBallotId}/adjudication")
    AdjudicationService.AdjudicationResult adjudicate(@PathVariable Long electionId,
                                                      @PathVariable Long provisionalBallotId,
                                                      @Valid @RequestBody Requests.Adjudicate request) {
        return adjudicationService.adjudicate(provisionalBallotId, request.accept(), request.reason());
    }

    /** 临时票裁定查询（可按状态过滤），不含票面内容与选民身份。 */
    @GetMapping("/provisional-ballots")
    List<QueryService.ProvisionalBallotView> provisionalBallots(
            @PathVariable Long electionId,
            @RequestParam(required = false) AdjudicationStatus status) {
        return queryService.provisionalBallots(electionId, status);
    }
}
