package org.aezden.backend.ingestion.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ingestion_job")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionJob {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private org.aezden.backend.cost.Provider provider;
    @Column(nullable = false)
    private UUID accountId;
    @Column(nullable = false)
    private LocalDate startDate;
    @Column(nullable = false)
    private LocalDate endDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IngestionStatus status;
    @Column(nullable = false)
    private Instant startedAt;
    private Instant finishedAt;
    private int recordsProcessed;
    private int recordsFailed;
    private int retries;
    private String errorMessage;
}
