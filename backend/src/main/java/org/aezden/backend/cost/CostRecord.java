package org.aezden.backend.cost;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "cost_record",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_cost_record_source",
                columnNames = {"provider", "account_id", "provider_record_id"}))
@Getter
@Setter
public class CostRecord {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private UUID accountId;
    @Enumerated(EnumType.STRING) private Provider provider;
    @Column(nullable = false)
    private String service;
    private String region;
    @Column(nullable = false)
    private LocalDate usageDate;
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal cost;
    @Column(nullable = false, length = 3)
    private String currency = "USD";
    @Column(name = "provider_record_id", nullable = false)
    private String providerRecordId;
    private UUID ingestionJobId;
}
