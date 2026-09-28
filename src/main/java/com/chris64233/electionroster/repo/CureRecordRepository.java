package com.chris64233.electionroster.repo;

import com.chris64233.electionroster.domain.CureRecord;
import com.chris64233.electionroster.domain.CureRecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CureRecordRepository extends JpaRepository<CureRecord, Long> {

    List<CureRecord> findByIssuanceIdOrderByVersionAsc(Long issuanceId);

    Optional<CureRecord> findByIssuanceIdAndVersion(Long issuanceId, int version);

    /** 当前待确认的材料版本（最多一条）。 */
    Optional<CureRecord> findByIssuanceIdAndStatus(Long issuanceId, CureRecordStatus status);

    long countByIssuanceId(Long issuanceId);
}
