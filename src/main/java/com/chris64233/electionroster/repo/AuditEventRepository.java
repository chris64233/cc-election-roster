package com.chris64233.electionroster.repo;

import com.chris64233.electionroster.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    Optional<AuditEvent> findTopByOrderByIdDesc();

    List<AuditEvent> findAllByOrderByIdAsc();
}
