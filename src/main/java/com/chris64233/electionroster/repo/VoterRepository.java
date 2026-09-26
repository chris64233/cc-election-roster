package com.chris64233.electionroster.repo;

import com.chris64233.electionroster.domain.Voter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VoterRepository extends JpaRepository<Voter, Long> {

    Optional<Voter> findByElectionIdAndVoterRef(Long electionId, String voterRef);
}
