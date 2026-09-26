package com.chris64233.electionroster.api;

import jakarta.validation.constraints.NotNull;

public class AdjudicateRequest {

    /** true=裁定通过计入选区；false=裁定拒绝永久作废。 */
    @NotNull
    private Boolean accepted;

    private String reason;

    public Boolean getAccepted() {
        return accepted;
    }

    public void setAccepted(Boolean accepted) {
        this.accepted = accepted;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
