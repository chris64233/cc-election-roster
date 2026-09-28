package com.chris64233.electionroster.api;

import jakarta.validation.constraints.NotBlank;

/**
 * 其他渠道有效投票登记。用于模拟“选民已通过其他渠道投票”的外部事实：
 * 与补正确认/临时票裁定竞争同一有效结果槽位。
 */
public class ExternalVoteRequest {

    @NotBlank
    private String voterRef;

    /** 外部渠道凭证/事件说明（追踪用，不含票面内容）。 */
    @NotBlank
    private String channelRef;

    public String getVoterRef() {
        return voterRef;
    }

    public void setVoterRef(String voterRef) {
        this.voterRef = voterRef;
    }

    public String getChannelRef() {
        return channelRef;
    }

    public void setChannelRef(String channelRef) {
        this.channelRef = channelRef;
    }
}
