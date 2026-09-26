package com.chris64233.electionroster.web;

import com.chris64233.electionroster.api.DistrictSummaryResponse;
import com.chris64233.electionroster.api.VoterStatusResponse;
import com.chris64233.electionroster.service.QueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class QueryController {

    private final QueryService queryService;

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    /** 选民签发状态（含临时票裁定结果），不含任何票面选择。 */
    @GetMapping("/elections/{electionId}/voters/{voterRef}/status")
    public VoterStatusResponse voterStatus(@PathVariable Long electionId, @PathVariable String voterRef) {
        return queryService.voterStatus(electionId, voterRef);
    }

    /** 选区汇总：签发/已消费/已计入/待裁定临时票。 */
    @GetMapping("/districts/{districtId}/summary")
    public DistrictSummaryResponse districtSummary(@PathVariable Long districtId) {
        return queryService.districtSummary(districtId);
    }

    /** 追加式哈希链审计记录，detail 仅含摘要，不含票面选择内容。 */
    @GetMapping("/audit")
    public List<QueryService.AuditEventView> auditChain() {
        return queryService.auditChain();
    }
}
