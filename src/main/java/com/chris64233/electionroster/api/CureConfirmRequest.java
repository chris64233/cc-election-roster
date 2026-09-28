package com.chris64233.electionroster.api;

/** 补正确认请求。确认前服务端重新核验截止时间、选民状态、选区与其他渠道投票。 */
public class CureConfirmRequest {

    /** 核验说明（身份侧），可选。 */
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
