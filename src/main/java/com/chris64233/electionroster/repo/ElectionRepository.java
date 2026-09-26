package com.chris64233.electionroster.repo;

import com.chris64233.electionroster.domain.Election;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ElectionRepository extends JpaRepository<Election, Long> {
}
