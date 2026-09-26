package com.chris64233.electionroster.web;

import com.chris64233.electionroster.domain.IssuanceType;
import com.chris64233.electionroster.domain.VoterStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Web 层请求 DTO。 */
final class Requests {

    private Requests() {
    }

    record CreateElection(@NotBlank String name) {
    }

    record CreatePrecinct(@NotBlank String code, @NotBlank String name) {
    }

    record CreateBallotStyle(@NotBlank String code, String description) {
    }

    record RegisterVoter(@NotBlank String voterRef,
                         @NotBlank String precinctCode,
                         @NotBlank String ballotStyleCode,
                         @NotNull VoterStatus status) {
    }

    record IssueBallot(@NotBlank String voterRef,
                       @NotBlank String eventId,
                       IssuanceType requestedType,
                       String precinctCode,
                       String pollingPlace,
                       String verificationMethod,
                       String verifierRef,
                       String verificationNotes) {
    }

    record SubmitBallot(@NotBlank String credentialToken, @NotBlank String choicePayload) {
    }

    record Adjudicate(boolean accept, String reason) {
    }
}
