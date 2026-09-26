package com.chris64233.electionroster.repository;

import com.chris64233.electionroster.domain.Precinct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PrecinctRepository extends JpaRepository<Precinct, Long> {

    Optional<Precinct> findByElectionIdAndCode(Long electionId, String code);
}
