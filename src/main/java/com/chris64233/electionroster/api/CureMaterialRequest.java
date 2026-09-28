package com.chris64233.electionroster.api;

import jakarta.validation.constraints.NotBlank;

/** 选民提交补正新材料。材料与票面选择隔离，仅用于恢复原选票。 */
public class CureMaterialRequest {

    /** 新材料说明（身份侧），不含票面选择。 */
    @NotBlank
    private String materialNotes;

    /** 可选：材料正文/证件摘要，服务端只保存其 SHA-256。 */
    private String materialContent;

    public String getMaterialNotes() {
        return materialNotes;
    }

    public void setMaterialNotes(String materialNotes) {
        this.materialNotes = materialNotes;
    }

    public String getMaterialContent() {
        return materialContent;
    }

    public void setMaterialContent(String materialContent) {
        this.materialContent = materialContent;
    }
}
