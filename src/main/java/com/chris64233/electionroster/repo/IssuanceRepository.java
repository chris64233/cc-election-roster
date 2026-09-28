package com.chris64233.electionroster.repo;

import com.chris64233.electionroster.domain.CureStatus;
import com.chris64233.electionroster.domain.Issuance;
import com.chris64233.electionroster.domain.IssuanceStatus;
import com.chris64233.electionroster.domain.IssuanceType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IssuanceRepository extends JpaRepository<Issuance, Long> {

    Optional<Issuance> findByElectionIdAndVoterId(Long electionId, Long voterId);

    Optional<Issuance> findByElectionIdAndEventNo(Long electionId, String eventNo);

    Optional<Issuance> findByCredentialToken(String credentialToken);

    /** 行级锁定按凭证读取：提交事务以加锁查询作为首次读取，避免与补正/其他渠道并发冲突。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Issuance i where i.credentialToken = :credentialToken")
    Optional<Issuance> findByCredentialTokenForUpdate(@Param("credentialToken") String credentialToken);

    /** 行级锁定按（选举,选民）读取：其他渠道登记以此作为首次读取。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Issuance i where i.election.id = :electionId and i.voter.id = :voterId")
    Optional<Issuance> findByElectionIdAndVoterIdForUpdate(@Param("electionId") Long electionId,
                                                           @Param("voterId") Long voterId);

    @Query("select i from Issuance i where i.election.id = :electionId and i.voter.voterRef = :voterRef")
    Optional<Issuance> findByElectionIdAndVoterRef(@Param("electionId") Long electionId,
                                                   @Param("voterRef") String voterRef);

    /** 只取 ID：供外部流程先定位再以加锁查询首次加载实体，避免同事务重复加载冲突。 */
    @Query("select i.id from Issuance i where i.election.id = :electionId and i.voter.voterRef = :voterRef")
    Optional<Long> findIdByElectionIdAndVoterRef(@Param("electionId") Long electionId,
                                                 @Param("voterRef") String voterRef);

    long countByDistrictIdAndStatus(Long districtId, IssuanceStatus status);

    /** 行级锁定读取，保证补正确认 / 截止裁定 / 跨渠道登记并发串行化。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Issuance i where i.id = :id")
    Optional<Issuance> findByIdForUpdate(@Param("id") Long id);

    /** 某次选举所有材料不全、暂不计入的邮寄票的 ID（截止裁定批量处理用，避免批量事务内重复加载）。 */
    @Query("select i.id from Issuance i where i.election.id = :electionId and i.type = :type "
            + "and i.cureStatus = :cureStatus")
    List<Long> findIdsByElectionIdAndTypeAndCureStatus(@Param("electionId") Long electionId,
                                                       @Param("type") IssuanceType type,
                                                       @Param("cureStatus") CureStatus cureStatus);

    long countByDistrictIdAndCureStatus(Long districtId, CureStatus cureStatus);
}
