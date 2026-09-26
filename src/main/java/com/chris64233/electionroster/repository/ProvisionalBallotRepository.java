package com.chris64233.electionroster.repository;

import com.chris64233.electionroster.domain.AdjudicationStatus;
import com.chris64233.electionroster.domain.ProvisionalBallot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProvisionalBallotRepository extends JpaRepository<ProvisionalBallot, Long> {

    Optional<ProvisionalBallot> findByIssuanceId(Long issuanceId);

    List<ProvisionalBallot> findByIssuanceElectionIdOrderById(Long electionId);

    List<ProvisionalBallot> findByIssuanceElectionIdAndAdjudicationStatusOrderById(
            Long electionId, AdjudicationStatus status);

    long countByIssuanceElectionIdAndIssuancePrecinctIdAndAdjudicationStatus(
            Long electionId, Long precinctId, AdjudicationStatus status);
}
