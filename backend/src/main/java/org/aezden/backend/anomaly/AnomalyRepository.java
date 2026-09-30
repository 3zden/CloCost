package org.aezden.backend.anomaly;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface AnomalyRepository extends JpaRepository<Anomaly, UUID> {
    List<Anomaly> findByStatusOrderByDetectedAtDesc(AnomalyStatus status);
}
