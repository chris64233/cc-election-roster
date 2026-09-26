package com.chris64233.electionroster.api;

/** 选区汇总：只含计数，不含任何票面选择内容。 */
public record DistrictSummaryResponse(
        String districtCode,
        long issuedTotal,
        long consumed,
        long countedBallots,
        long pendingProvisionals) {
}
