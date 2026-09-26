package com.chris64233.electionroster.repo;

import com.chris64233.electionroster.domain.BallotStyle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BallotStyleRepository extends JpaRepository<BallotStyle, Long> {
}
