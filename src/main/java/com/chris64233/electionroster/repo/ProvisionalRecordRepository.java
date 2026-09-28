package com.chris64233.electionroster.repo;

import com.chris64233.electionroster.domain.Adjudication;
import com.chris64233.electionroster.domain.ProvisionalRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProvisionalRecordRepository extends JpaRepository<ProvisionalRecord, Long> {

    Optional<ProvisionalRecord> findByIssuanceId(Long issuanceId);

    /** 行级锁定读取，保证临时票裁定 / 补正确认 / 截止裁定并发串行化。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProvisionalRecord p where p.issuance.id = :issuanceId")
    Optional<ProvisionalRecord> findByIssuanceIdForUpdate(@Param("issuanceId") Long issuanceId);

    List<ProvisionalRecord> findByAdjudication(Adjudication adjudication);

    /** 某次选举内处于指定裁定状态的临时票签发记录 ID（截止裁定用）。 */
    @Query("select p.issuance.id from ProvisionalRecord p "
            + "where p.issuance.election.id = :electionId and p.adjudication = :adjudication")
    List<Long> findIssuanceIdsByElectionIdAndAdjudication(@Param("electionId") Long electionId,
                                                          @Param("adjudication") Adjudication adjudication);

    long countByIssuance_District_IdAndAdjudication(Long districtId, Adjudication adjudication);
}
