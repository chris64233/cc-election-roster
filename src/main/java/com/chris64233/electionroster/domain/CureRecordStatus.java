package com.chris64233.electionroster.domain;

/** 补正材料版本的处理状态。 */
public enum CureRecordStatus {
    /** 已提交，等待确认。 */
    PENDING,
    /** 确认通过，原选票恢复有效。 */
    CONFIRMED,
    /** 确认失败（资格、选区、其他渠道投票或截止）。 */
    FAILED,
    /** 被更新的材料版本替代。 */
    SUPERSEDED
}
