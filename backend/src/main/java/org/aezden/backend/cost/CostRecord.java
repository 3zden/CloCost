package org.aezden.backend.cost;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "cost_records")
@Getter
@Setter
public class CostRecord {

    @Id @GeneratedValue private UUID id;
    private UUID accountId;
    @Enumerated(EnumType.STRING) private Provider provider;
    private String service;
    private String region;
    private LocalDate usageDate;
    private BigDecimal cost;
    private String currency;
}
