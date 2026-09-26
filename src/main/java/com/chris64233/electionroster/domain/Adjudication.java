package com.chris64233.electionroster.domain;

/** 临时票裁定状态。 */
public enum Adjudication {
    PENDING,
    /** 裁定通过，对应选票计入选区。 */
    ACCEPTED,
    /** 裁定拒绝，对应选票永久作废。 */
    REJECTED
}
