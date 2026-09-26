package com.chris64233.electionroster.repo;

import com.chris64233.electionroster.domain.District;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DistrictRepository extends JpaRepository<District, Long> {
}
