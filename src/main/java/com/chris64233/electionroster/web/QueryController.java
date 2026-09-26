package com.chris64233.electionroster.web;

import com.chris64233.electionroster.service.QueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 选民签发状态与选区汇总查询端点。 */
@RestController
@RequestMapping("/api/elections/{electionId}")
public class QueryController {

    private final QueryService queryService;

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    /** 选民签发状态查询：只含签发状态，不含票面选择。 */
    @GetMapping("/voters/{voterRef}/issuance-status")
    QueryService.VoterIssuanceStatusView voterIssuanceStatus(@PathVariable Long electionId,
                                                             @PathVariable String voterRef) {
        return queryService.voterIssuanceStatus(electionId, voterRef);
    }

    /** 选区汇总查询：只含计数，不含票面内容与选民身份。 */
    @GetMapping("/precincts/{precinctCode}/summary")
    QueryService.PrecinctSummaryView precinctSummary(@PathVariable Long electionId,
                                                     @PathVariable String precinctCode) {
        return queryService.precinctSummary(electionId, precinctCode);
    }
}
