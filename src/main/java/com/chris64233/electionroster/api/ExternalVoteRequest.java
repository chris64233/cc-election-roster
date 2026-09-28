package com.chris64233.electionroster.api;

import jakarta.validation.constraints.NotBlank;

/** 登记选民已通过其他渠道有效投票。 */
public class ExternalVoteRequest {

    @NotBlank
    private String voterRef;

    /** 其他投票渠道标识，如 OTHER_POLLING_PLACE / EXTERNAL_SYSTEM。 */
    @NotBlank
    private String channel;

    /** 外部凭证号，用于跨渠道凭证消费追踪。 */
    @NotBlank
    private String externalRef;

    public String getVoterRef() {
        return voterRef;
    }

    public void setVoterRef(String voterRef) {
        this.voterRef = voterRef;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public void setExternalRef(String externalRef) {
        this.externalRef = externalRef;
    }
}
