package com.chris64233.electionroster.repo;

import com.chris64233.electionroster.domain.CureMaterial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CureMaterialRepository extends JpaRepository<CureMaterial, Long> {

    List<CureMaterial> findByCureRecordIdOrderByVersionAsc(Long cureRecordId);

    long countByCureRecordId(Long cureRecordId);
}
