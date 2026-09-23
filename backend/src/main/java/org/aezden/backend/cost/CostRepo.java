package org.aezden.backend.cost;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CostRepo extends JpaRepository<CostRecord, UUID> {

    List<CostRecord> getAllByUsageDateBefore(LocalDate now);

    @Query("""
            SELECT c FROM CostRecord c
            WHERE (:provider IS NULL OR CAST(c.provider AS string) = :provider)
            AND (:accountId IS NULL OR CAST(c.accountId AS string) = :accountId)
            AND (CAST(:startDate AS LocalDate) IS NULL OR c.usageDate >= :startDate)
            AND (CAST(:endDate AS LocalDate) IS NULL OR c.usageDate <= :endDate)
            ORDER BY c.usageDate
            """)
    List<CostRecord> findCosts(
            @Param("provider") String provider,
            @Param("accountId") String accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
