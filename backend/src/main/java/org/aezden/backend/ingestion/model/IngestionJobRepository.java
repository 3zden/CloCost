package org.aezden.backend.ingestion.model;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface IngestionJobRepository extends JpaRepository<IngestionJob, UUID> {
}
