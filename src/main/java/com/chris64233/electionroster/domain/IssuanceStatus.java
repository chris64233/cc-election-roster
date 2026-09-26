package com.chris64233.electionroster.domain;

/** 签发凭证生命周期：签发后只能单向流转，凭证本身不可修改。 */
public enum IssuanceStatus {
    /** 已签发，尚未提交选票。 */
    ISSUED,
    /** 凭证已被一次有效提交消费。 */
    CONSUMED,
    /** 永久作废（如临时票裁定拒绝）。 */
    VOIDED
}
