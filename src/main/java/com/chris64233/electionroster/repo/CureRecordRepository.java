package com.chris64233.electionroster.repo;

import com.chris64233.electionroster.domain.CureRecord;
import com.chris64233.electionroster.domain.CureStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CureRecordRepository extends JpaRepository<CureRecord, Long> {

    Optional<CureRecord> findByIssuanceId(Long issuanceId);

    List<CureRecord> findByStatus(CureStatus status);

    long countByIssuance_District_IdAndStatus(Long districtId, CureStatus status);
}
