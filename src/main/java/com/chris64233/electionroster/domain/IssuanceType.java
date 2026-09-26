package com.chris64233.electionroster.domain;

/** 签发类型。三种类型共享同一选民唯一性约束。 */
public enum IssuanceType {
    /** 投票站正式选票。 */
    OFFICIAL,
    /** 资格争议时签发的临时票。 */
    PROVISIONAL,
    /** 邮寄票登记。 */
    MAIL
}
