package com.chris64233.electionroster.domain;

/** 有效投票结果的来源。 */
public enum EffectiveVoteSource {
    /** 常规渠道直接提交即计入（正式票、材料齐全邮寄票、已通过裁定的临时票）。 */
    DIRECT_SUBMISSION,
    /** 临时票裁定通过。 */
    PROVISIONAL_ACCEPTED,
    /** 补正确认通过，原选票恢复有效。 */
    CURE_CONFIRMED,
    /** 选民已通过其他渠道（如另一投票点、其他投票方式）有效投票。 */
    EXTERNAL_CHANNEL
}
