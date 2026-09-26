package com.chris64233.electionroster.repo;

import com.chris64233.electionroster.domain.Adjudication;
import com.chris64233.electionroster.domain.ProvisionalRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProvisionalRecordRepository extends JpaRepository<ProvisionalRecord, Long> {

    Optional<ProvisionalRecord> findByIssuanceId(Long issuanceId);

    List<ProvisionalRecord> findByAdjudication(Adjudication adjudication);

    long countByIssuance_District_IdAndAdjudication(Long districtId, Adjudication adjudication);
}
