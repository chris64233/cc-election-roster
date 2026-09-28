package com.chris64233.electionroster.api;

import jakarta.validation.constraints.NotBlank;

public class SubmitRequest {

    @NotBlank
    private String credentialToken;

    /** 票面选择（JSON 字符串），只进入隔离的选票内容表。 */
    @NotBlank
    private String choicesJson;

    /**
     * 身份材料是否不全。为 true 时（仅对邮寄票/临时票生效），选票暂存不计入，
     * 须在补正截止前通过补正确认恢复。缺省视为材料齐全。
     */
    private Boolean identityIncomplete;

    /** identityIncomplete=true 时的缺件说明（身份侧信息，与票面选择隔离）。 */
    private String missingMaterials;

    public String getCredentialToken() {
        return credentialToken;
    }

    public void setCredentialToken(String credentialToken) {
        this.credentialToken = credentialToken;
    }

    public String getChoicesJson() {
        return choicesJson;
    }

    public void setChoicesJson(String choicesJson) {
        this.choicesJson = choicesJson;
    }

    public Boolean getIdentityIncomplete() {
        return identityIncomplete;
    }

    public void setIdentityIncomplete(Boolean identityIncomplete) {
        this.identityIncomplete = identityIncomplete;
    }

    public String getMissingMaterials() {
        return missingMaterials;
    }

    public void setMissingMaterials(String missingMaterials) {
        this.missingMaterials = missingMaterials;
    }
}
