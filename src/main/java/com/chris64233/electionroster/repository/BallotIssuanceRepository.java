package com.chris64233.electionroster.repository;

import com.chris64233.electionroster.domain.BallotIssuance;
import com.chris64233.electionroster.domain.IssuanceType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BallotIssuanceRepository extends JpaRepository<BallotIssuance, Long> {

    @EntityGraph(attributePaths = {"voter", "precinct", "ballotStyle"})
    Optional<BallotIssuance> findByEventId(String eventId);

    @EntityGraph(attributePaths = {"voter", "precinct", "ballotStyle"})
    Optional<BallotIssuance> findByCredentialToken(String credentialToken);

    Optional<BallotIssuance> findByElectionIdAndVoterId(Long electionId, Long voterId);

    boolean existsByElectionIdAndVoterId(Long electionId, Long voterId);

    long countByElectionIdAndPrecinctIdAndType(Long electionId, Long precinctId, IssuanceType type);
}
