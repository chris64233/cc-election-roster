package com.chris64233.electionroster.web;

import com.chris64233.electionroster.domain.BallotStyle;
import com.chris64233.electionroster.domain.Election;
import com.chris64233.electionroster.domain.Precinct;
import com.chris64233.electionroster.domain.Voter;
import com.chris64233.electionroster.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 选举、选区、选票样式与名册登记端点。 */
@RestController
@RequestMapping("/api/elections")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> createElection(@Valid @RequestBody Requests.CreateElection request) {
        Election election = registrationService.createElection(request.name());
        return Map.of("id", election.getId(), "name", election.getName());
    }

    @PostMapping("/{electionId}/precincts")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> createPrecinct(@PathVariable Long electionId,
                                       @Valid @RequestBody Requests.CreatePrecinct request) {
        Precinct precinct = registrationService.createPrecinct(electionId, request.code(), request.name());
        return Map.of("id", precinct.getId(), "code", precinct.getCode(), "name", precinct.getName());
    }

    @PostMapping("/{electionId}/ballot-styles")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> createBallotStyle(@PathVariable Long electionId,
                                          @Valid @RequestBody Requests.CreateBallotStyle request) {
        BallotStyle style = registrationService.createBallotStyle(
                electionId, request.code(), request.description());
        return Map.of("id", style.getId(), "code", style.getCode());
    }

    @PostMapping("/{electionId}/voters")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> registerVoter(@PathVariable Long electionId,
                                      @Valid @RequestBody Requests.RegisterVoter request) {
        Voter voter = registrationService.registerVoter(electionId, request.voterRef(),
                request.precinctCode(), request.ballotStyleCode(), request.status());
        return Map.of("id", voter.getId(), "voterRef", voter.getVoterRef(),
                "status", voter.getStatus().name());
    }
}
