package org.aezden.backend.cost;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CostRepo extends JpaRepository<CostRecord, UUID> {

    List<CostRecord> findByUsageDateLessThanEqual(LocalDate date);

    boolean existsByProviderAndAccountIdAndProviderRecordId(
            Provider provider, UUID accountId, String providerRecordId);

    @Modifying
    @Query(value = """
            INSERT INTO cost_record
                (id, account_id, provider, service, region, usage_date, cost, currency,
                 provider_record_id, ingestion_job_id)
            VALUES (gen_random_uuid(), :accountId, :provider, :service, :region, :usageDate,
                    :cost, :currency, :providerRecordId, :jobId)
            ON CONFLICT (provider, account_id, provider_record_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("accountId") UUID accountId,
            @Param("provider") String provider,
            @Param("service") String service,
            @Param("region") String region,
            @Param("usageDate") LocalDate usageDate,
            @Param("cost") java.math.BigDecimal cost,
            @Param("currency") String currency,
            @Param("providerRecordId") String providerRecordId,
            @Param("jobId") UUID jobId);

    @Query("""
            SELECT c FROM CostRecord c
            WHERE (:provider IS NULL OR c.provider = :provider)
            AND (:accountId IS NULL OR c.accountId = :accountId)
            AND (CAST(:startDate AS LocalDate) IS NULL OR c.usageDate >= :startDate)
            AND (CAST(:endDate AS LocalDate) IS NULL OR c.usageDate <= :endDate)
            ORDER BY c.usageDate
            """)
    List<CostRecord> findCosts(
            @Param("provider") Provider provider,
            @Param("accountId") UUID accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
