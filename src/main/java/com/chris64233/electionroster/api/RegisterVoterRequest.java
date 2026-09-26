package com.chris64233.electionroster.api;

import jakarta.validation.constraints.NotBlank;

public class RegisterVoterRequest {

    @NotBlank
    private String electionName;

    @NotBlank
    private String districtCode;

    @NotBlank
    private String ballotStyleCode;

    @NotBlank
    private String voterRef;

    /** ELIGIBLE / DISPUTED / INELIGIBLE，默认 ELIGIBLE。 */
    private String voterStatus = "ELIGIBLE";

    public String getElectionName() {
        return electionName;
    }

    public void setElectionName(String electionName) {
        this.electionName = electionName;
    }

    public String getDistrictCode() {
        return districtCode;
    }

    public void setDistrictCode(String districtCode) {
        this.districtCode = districtCode;
    }

    public String getBallotStyleCode() {
        return ballotStyleCode;
    }

    public void setBallotStyleCode(String ballotStyleCode) {
        this.ballotStyleCode = ballotStyleCode;
    }

    public String getVoterRef() {
        return voterRef;
    }

    public void setVoterRef(String voterRef) {
        this.voterRef = voterRef;
    }

    public String getVoterStatus() {
        return voterStatus;
    }

    public void setVoterStatus(String voterStatus) {
        this.voterStatus = voterStatus;
    }
}
