package com.chris64233.electionroster.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class IssueRequest {

    @NotNull
    private Long electionId;

    @NotBlank
    private String voterRef;

    /** 签发事件号：同一事件号重复请求返回同一结果（幂等）。 */
    @NotBlank
    private String eventNo;

    /** 发起签发的投票点。 */
    @NotBlank
    private String pollingPlace;

    /** OFFICIAL / PROVISIONAL / MAIL；与选民资格状态必须匹配。 */
    @NotBlank
    private String type;

    /** 可选：投票点核验的选区，与名册不一致则拒绝。 */
    private String expectedDistrictCode;

    /** 仅临时签发使用：身份核验信息，与票面内容分离保存。 */
    private String identityNotes;

    public Long getElectionId() {
        return electionId;
    }

    public void setElectionId(Long electionId) {
        this.electionId = electionId;
    }

    public String getVoterRef() {
        return voterRef;
    }

    public void setVoterRef(String voterRef) {
        this.voterRef = voterRef;
    }

    public String getEventNo() {
        return eventNo;
    }

    public void setEventNo(String eventNo) {
        this.eventNo = eventNo;
    }

    public String getPollingPlace() {
        return pollingPlace;
    }

    public void setPollingPlace(String pollingPlace) {
        this.pollingPlace = pollingPlace;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getExpectedDistrictCode() {
        return expectedDistrictCode;
    }

    public void setExpectedDistrictCode(String expectedDistrictCode) {
        this.expectedDistrictCode = expectedDistrictCode;
    }

    public String getIdentityNotes() {
        return identityNotes;
    }

    public void setIdentityNotes(String identityNotes) {
        this.identityNotes = identityNotes;
    }
}
