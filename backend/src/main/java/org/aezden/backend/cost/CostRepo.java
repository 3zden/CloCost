package org.aezden.backend.cost;

import org.aezden.backend.cost.dto.CostResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public interface CostRepo extends JpaRepository<CostRecord, UUID> {

    List<CostResponse> getAllByDateBefore(LocalDate now);


    @Query("""
            SELECT c FROM CostRecord c
            WHERE :provider IS NULL OR c.provider = :provider
            AND :accoundId IS NULL OR c.accountId = :accountId
            AND :startDate IS NULL OR c.usageDate >= :startDate
            AND :endDate IS NULL OR c.usageDate <= :endDate
            ORDER BY c.usageDate
            """)
    List<CostResponse> findCosts(
            @Param("provider") String provider,
            @Param("accountId") String accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
