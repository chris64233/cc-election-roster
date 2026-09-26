package com.chris64233.electionroster.api;

import jakarta.validation.constraints.NotBlank;

public class SubmitRequest {

    @NotBlank
    private String credentialToken;

    /** 票面选择（JSON 字符串），只进入隔离的选票内容表。 */
    @NotBlank
    private String choicesJson;

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
}
