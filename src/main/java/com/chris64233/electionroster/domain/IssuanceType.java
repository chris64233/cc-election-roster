package com.chris64233.electionroster.domain;

/** 签发类型。三种类型共享同一（选举, 选民）唯一性约束。 */
public enum IssuanceType {
    /** 投票点正式签发。 */
    OFFICIAL,
    /** 资格争议时的临时票签发。 */
    PROVISIONAL,
    /** 邮寄票登记。 */
    MAIL
}
