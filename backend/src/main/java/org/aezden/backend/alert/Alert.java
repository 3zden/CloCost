package org.aezden.backend.alert;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alert")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Alert {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID budgetId;
    private UUID anomalyId;
    @Column(nullable = false)
    private String message;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private AlertSeverity severity;
    @Column(nullable = false)
    private Instant triggeredAt;
}
