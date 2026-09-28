package com.chris64233.electionroster.repo;

import com.chris64233.electionroster.domain.BallotContent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BallotContentRepository extends JpaRepository<BallotContent, String> {

    long countByDistrictIdAndCountedTrue(Long districtId);

    long countByDistrictIdAndHeldTrue(Long districtId);
}
