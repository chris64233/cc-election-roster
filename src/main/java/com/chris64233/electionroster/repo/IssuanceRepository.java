package com.chris64233.electionroster.repo;

import com.chris64233.electionroster.domain.Issuance;
import com.chris64233.electionroster.domain.IssuanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IssuanceRepository extends JpaRepository<Issuance, Long> {

    Optional<Issuance> findByElectionIdAndVoterId(Long electionId, Long voterId);

    Optional<Issuance> findByElectionIdAndEventNo(Long electionId, String eventNo);

    Optional<Issuance> findByCredentialToken(String credentialToken);

    @Query("select i from Issuance i where i.election.id = :electionId and i.voter.voterRef = :voterRef")
    Optional<Issuance> findByElectionIdAndVoterRef(@Param("electionId") Long electionId,
                                                   @Param("voterRef") String voterRef);

    long countByDistrictIdAndStatus(Long districtId, IssuanceStatus status);
}
