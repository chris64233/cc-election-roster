package com.chris64233.electionroster.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 补正材料提交：关联原选票的签发记录，产生材料新版本，不重新签发凭证。 */
public class CureSubmitRequest {

    /** 原选票签发记录 ID。 */
    @NotNull
    private Long issuanceId;

    /** 新提交的身份材料说明，不含任何票面选择。 */
    @NotBlank
    private String materialNotes;

    public Long getIssuanceId() {
        return issuanceId;
    }

    public void setIssuanceId(Long issuanceId) {
        this.issuanceId = issuanceId;
    }

    public String getMaterialNotes() {
        return materialNotes;
    }

    public void setMaterialNotes(String materialNotes) {
        this.materialNotes = materialNotes;
    }
}
