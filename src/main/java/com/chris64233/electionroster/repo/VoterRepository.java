package com.chris64233.electionroster.repo;

import com.chris64233.electionroster.domain.Voter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VoterRepository extends JpaRepository<Voter, Long> {

    Optional<Voter> findByElectionIdAndVoterRef(Long electionId, String voterRef);

    /**
     * 对选民行加写锁。补正确认、其他渠道投票登记、截止裁定三个并发流程
     * 都先竞争同一选民行锁，使“唯一有效结果”的判定串行化、原子化。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Voter v where v.id = :id")
    Optional<Voter> findForLockById(@Param("id") Long id);
}
