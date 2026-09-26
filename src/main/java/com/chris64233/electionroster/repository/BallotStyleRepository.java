package com.chris64233.electionroster.repository;

import com.chris64233.electionroster.domain.BallotStyle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BallotStyleRepository extends JpaRepository<BallotStyle, Long> {

    Optional<BallotStyle> findByElectionIdAndCode(Long electionId, String code);
}
