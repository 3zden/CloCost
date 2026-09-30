package org.aezden.backend.rightsizing;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface RightsizingRepository extends JpaRepository<RightsizingRecommendation, UUID> {
    List<RightsizingRecommendation> findAllByOrderByCreatedAtDesc();
}
