package org.aezden.backend.alert;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface AlertRepository extends JpaRepository<Alert, UUID> {
    List<Alert> findAllByOrderByTriggeredAtDesc();
}
