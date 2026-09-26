package com.chris64233.electionroster.domain;

/** 临时票裁定结果。 */
public enum AdjudicationStatus {
    PENDING,
    /** 裁定通过，临时票计入对应选区。 */
    ACCEPTED,
    /** 裁定拒绝，临时票永久作废。 */
    REJECTED
}
