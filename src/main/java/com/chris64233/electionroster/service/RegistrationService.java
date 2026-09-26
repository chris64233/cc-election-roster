package com.chris64233.electionroster.service;

import com.chris64233.electionroster.domain.BallotStyle;
import com.chris64233.electionroster.domain.Election;
import com.chris64233.electionroster.domain.Precinct;
import com.chris64233.electionroster.domain.Voter;
import com.chris64233.electionroster.domain.VoterStatus;
import com.chris64233.electionroster.repository.BallotStyleRepository;
import com.chris64233.electionroster.repository.ElectionRepository;
import com.chris64233.electionroster.repository.PrecinctRepository;
import com.chris64233.electionroster.repository.VoterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 选举、选区、选票样式与名册的登记服务。 */
@Service
public class RegistrationService {

    private final ElectionRepository electionRepository;
    private final PrecinctRepository precinctRepository;
    private final BallotStyleRepository ballotStyleRepository;
    private final VoterRepository voterRepository;

    public RegistrationService(ElectionRepository electionRepository,
                               PrecinctRepository precinctRepository,
                               BallotStyleRepository ballotStyleRepository,
                               VoterRepository voterRepository) {
        this.electionRepository = electionRepository;
        this.precinctRepository = precinctRepository;
        this.ballotStyleRepository = ballotStyleRepository;
        this.voterRepository = voterRepository;
    }

    @Transactional
    public Election createElection(String name) {
        return electionRepository.save(new Election(name));
    }

    @Transactional
    public Precinct createPrecinct(Long electionId, String code, String name) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new NotFoundException("选举不存在: " + electionId));
        return precinctRepository.save(new Precinct(election, code, name));
    }

    @Transactional
    public BallotStyle createBallotStyle(Long electionId, String code, String description) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new NotFoundException("选举不存在: " + electionId));
        return ballotStyleRepository.save(new BallotStyle(election, code, description));
    }

    @Transactional
    public Voter registerVoter(Long electionId, String voterRef, String precinctCode,
                               String ballotStyleCode, VoterStatus status) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new NotFoundException("选举不存在: " + electionId));
        Precinct precinct = precinctRepository.findByElectionIdAndCode(electionId, precinctCode)
                .orElseThrow(() -> new NotFoundException("选区不存在: " + precinctCode));
        BallotStyle style = ballotStyleRepository.findByElectionIdAndCode(electionId, ballotStyleCode)
                .orElseThrow(() -> new NotFoundException("选票样式不存在: " + ballotStyleCode));
        return voterRepository.save(new Voter(election, voterRef, precinct, style, status));
    }
}
