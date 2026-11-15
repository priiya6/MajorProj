package com.opssentinel.diagnosis.repository;

import com.opssentinel.diagnosis.entity.PastIncidentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PastIncidentRepository extends JpaRepository<PastIncidentEntity, Long> {
    // TODO: once the corpus grows large, add pagination here instead of
    //       loading the full table in DiagnosisService.retrieveTopKSimilar()
}
