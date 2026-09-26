package com.chris64233.electionroster.domain;

/** 选民名册状态。 */
public enum VoterStatus {
    /** 资格有效，可签发正式票或登记邮寄票。 */
    ELIGIBLE,
    /** 资格存在争议，只能签发临时票。 */
    DISPUTED,
    /** 无资格，禁止任何签发。 */
    INELIGIBLE
}
