package org.aezden.backend.rightsizing;

import jakarta.persistence.*;
import lombok.*;
import org.aezden.backend.cost.Provider;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rightsizing_recommendation")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RightsizingRecommendation {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private String resourceId;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Provider provider;
    @Column(nullable = false)
    private String service;
    @Column(nullable = false)
    private String recommendation;
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal currentMonthlyCost;
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal estimatedSavings;
    @Column(nullable = false)
    private String reason;
    @Column(nullable = false)
    private Instant createdAt;
}
