package com.chris64233.electionroster.repository;

import com.chris64233.electionroster.domain.IdentityVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdentityVerificationRepository extends JpaRepository<IdentityVerification, Long> {

    Optional<IdentityVerification> findByIssuanceId(Long issuanceId);
}
