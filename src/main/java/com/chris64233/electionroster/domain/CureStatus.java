package com.chris64233.electionroster.domain;

/**
 * 补正（身份材料补全）状态。
 * 补正只用于恢复原选票的有效性，不重新签发选票。
 */
public enum CureStatus {
    /** 选票因身份材料不全被暂存，等待选民补正。 */
    PENDING,
    /** 补正材料核验通过，原选票恢复计入。终态。 */
    CONFIRMED,
    /** 补正被拒绝（材料不合格、资格不成立等），原选票作废。终态。 */
    REJECTED,
    /** 该选民已通过其他渠道有效投票，本补正失效。终态。 */
    SUPERSEDED,
    /** 超过补正截止时间仍未确认，原选票作废。终态。 */
    EXPIRED
}
