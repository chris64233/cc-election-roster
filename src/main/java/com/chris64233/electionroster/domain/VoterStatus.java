package com.chris64233.electionroster.domain;

/** 选民在名册中的资格状态。 */
public enum VoterStatus {
    /** 资格有效，可签发正式票或登记邮寄票。 */
    ELIGIBLE,
    /** 资格存在争议，只能签发临时票。 */
    DISPUTED,
    /** 资格无效，禁止签发任何选票。 */
    INELIGIBLE
}
