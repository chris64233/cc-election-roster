package com.chris64233.electionroster.repo;

import com.chris64233.electionroster.domain.ExternalVoteRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExternalVoteRecordRepository extends JpaRepository<ExternalVoteRecord, Long> {

    Optional<ExternalVoteRecord> findByElectionIdAndVoterId(Long electionId, Long voterId);
}
