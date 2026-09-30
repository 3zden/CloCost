package org.aezden.backend.anomaly;

import jakarta.persistence.*;
import lombok.*;
import org.aezden.backend.cost.Provider;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "anomaly")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Anomaly {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Provider provider;
    @Column(nullable = false)
    private String service;
    @Column(nullable = false)
    private Instant detectedAt;
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal expectedCost;
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal actualCost;
    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal deviationPercent;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private AnomalyStatus status;
}
