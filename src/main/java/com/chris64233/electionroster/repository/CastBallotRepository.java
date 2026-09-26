package com.chris64233.electionroster.repository;

import com.chris64233.electionroster.domain.CastBallot;
import com.chris64233.electionroster.domain.IssuanceType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CastBallotRepository extends JpaRepository<CastBallot, Long> {

    long countByElectionIdAndPrecinctId(Long electionId, Long precinctId);

    long countByElectionIdAndPrecinctIdAndSourceType(Long electionId, Long precinctId, IssuanceType sourceType);
}
