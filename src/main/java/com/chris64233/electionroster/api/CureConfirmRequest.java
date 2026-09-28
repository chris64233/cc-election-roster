package com.chris64233.electionroster.api;

import jakarta.validation.constraints.NotNull;

/** 补正确认请求：确认前重新核验选区与选民状态。 */
public class CureConfirmRequest {

    /** 待确认的材料版本；为 null 时确认当前最新版本。 */
    private Integer version;

    /** 可选：确认时投票点重新核验的选区码，与名册不一致则确认失败。 */
    private String expectedDistrictCode;

    @NotNull
    private Long issuanceId;

    public Long getIssuanceId() {
        return issuanceId;
    }

    public void setIssuanceId(Long issuanceId) {
        this.issuanceId = issuanceId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getExpectedDistrictCode() {
        return expectedDistrictCode;
    }

    public void setExpectedDistrictCode(String expectedDistrictCode) {
        this.expectedDistrictCode = expectedDistrictCode;
    }
}
