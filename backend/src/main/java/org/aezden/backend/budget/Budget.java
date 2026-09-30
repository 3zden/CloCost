package org.aezden.backend.budget;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "budget")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Budget {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal limitAmount;
    @Column(nullable = false, length = 3)
    private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private BudgetPeriod period;
    @Column(nullable = false)
    private Instant createdAt;
}
