package com.chris64233.electionroster.domain;

/** 邮寄票补正流程状态（仅邮寄票使用；临时票的暂存状态由裁定 PENDING 表达）。 */
public enum CureStatus {
    /** 不需要补正（材料齐全或非邮寄票）。 */
    NOT_REQUIRED,
    /** 身份材料不全，选票暂不计入，等待补正确认。 */
    PENDING,
    /** 补正确认通过，原选票恢复有效。 */
    CONFIRMED,
    /** 补正失败（截止、资格变化或已通过其他渠道投票），选票作废。 */
    FAILED
}
