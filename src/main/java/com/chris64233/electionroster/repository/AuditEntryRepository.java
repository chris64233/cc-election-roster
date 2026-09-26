package com.chris64233.electionroster.repository;

import com.chris64233.electionroster.domain.AuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    Optional<AuditEntry> findTopByElectionIdOrderByIdDesc(Long electionId);

    List<AuditEntry> findByElectionIdOrderById(Long electionId);
}
