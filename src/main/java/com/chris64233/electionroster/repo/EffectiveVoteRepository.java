package com.chris64233.electionroster.repo;

import com.chris64233.electionroster.domain.EffectiveVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EffectiveVoteRepository extends JpaRepository<EffectiveVote, Long> {

    Optional<EffectiveVote> findByElectionIdAndVoterId(Long electionId, Long voterId);

    boolean existsByElectionIdAndVoterId(Long electionId, Long voterId);
}
