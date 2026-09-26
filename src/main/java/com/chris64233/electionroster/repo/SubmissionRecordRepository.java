package com.chris64233.electionroster.repo;

import com.chris64233.electionroster.domain.SubmissionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubmissionRecordRepository extends JpaRepository<SubmissionRecord, Long> {

    Optional<SubmissionRecord> findByCredentialToken(String credentialToken);
}
