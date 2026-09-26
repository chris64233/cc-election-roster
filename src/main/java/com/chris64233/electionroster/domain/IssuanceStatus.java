package com.chris64233.electionroster.domain;

/** 签发凭证生命周期。 */
public enum IssuanceStatus {
    /** 已签发，尚未被提交消费。 */
    ISSUED,
    /** 已被一次有效提交消费。 */
    CONSUMED,
    /** 已永久作废（如临时票裁定拒绝）。 */
    VOIDED
}
