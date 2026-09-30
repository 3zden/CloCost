package org.aezden.backend.ingestion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aezden.backend.cost.*;
import org.aezden.backend.ingestion.model.*;
import org.aezden.backend.ingestion.provider.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {
    private final IngestionJobRepository jobRepository;
    private final CostRepo costRepository;
    private final List<CloudCostProvider> costProviders;

    @Transactional(noRollbackFor = RuntimeException.class)
    public IngestionJob ingest(Provider provider, UUID accountId, LocalDate startDate, LocalDate endDate) {
        return ingest(provider, accountId, accountId == null ? null : accountId.toString(), startDate, endDate);
    }

    @Transactional(noRollbackFor = RuntimeException.class)
    public IngestionJob ingest(
            Provider provider,
            UUID accountId,
            String providerAccountId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        validateRequest(provider, accountId, startDate, endDate);
        if (providerAccountId == null || providerAccountId.isBlank()) {
            throw new IllegalArgumentException("providerAccountId is required");
        }
        Map<Provider, CloudCostProvider> providers = costProviders.stream()
                .collect(Collectors.toUnmodifiableMap(CloudCostProvider::provider, Function.identity()));
        CloudCostProvider costProvider = providers.get(provider);
        if (costProvider == null) {
            throw new IllegalArgumentException("No cost provider configured for " + provider);
        }
        IngestionJob job = jobRepository.save(IngestionJob.builder()
                .provider(provider).accountId(accountId).startDate(startDate).endDate(endDate)
                .status(IngestionStatus.RUNNING).startedAt(Instant.now()).build());
        int processed = 0;
        int failed = 0;
        try {
            List<RawCostRecord> records = costProvider.fetchCosts(providerAccountId, startDate, endDate);
            for (RawCostRecord raw : records) {
                try {
                    validateRecord(raw);
                    String sourceId = sourceId(raw);
                    costRepository.insertIfAbsent(accountId, provider.name(), raw.service(),
                            raw.region(), raw.usageDate(), raw.cost(),
                            currency(raw.currency()), sourceId, job.getId());
                    processed++;
                } catch (RuntimeException exception) {
                    failed++;
                    log.warn("Skipping invalid cost record for ingestion job {}: {}",
                            job.getId(), exception.getMessage());
                }
            }
            job.setStatus(failed == 0 ? IngestionStatus.SUCCESS : IngestionStatus.SUCCESS_WITH_ERRORS);
            job.setRecordsProcessed(processed);
            job.setRecordsFailed(failed);
            job.setFinishedAt(Instant.now());
            return jobRepository.save(job);
        } catch (RuntimeException exception) {
            job.setStatus(IngestionStatus.FAILED);
            job.setErrorMessage(exception.getMessage());
            job.setFinishedAt(Instant.now());
            jobRepository.save(job);
            throw exception;
        }
    }

    public IngestionJob ingestAws(UUID accountId, LocalDate startDate, LocalDate endDate) {
        return ingest(Provider.AWS, accountId, startDate, endDate);
    }

    private void validateRequest(Provider provider, UUID accountId, LocalDate startDate, LocalDate endDate) {
        if (provider == null || accountId == null || startDate == null || endDate == null) {
            throw new IllegalArgumentException("provider, accountId, startDate and endDate are required");
        }
        if (!startDate.isBefore(endDate)) {
            throw new IllegalArgumentException("startDate must be before endDate");
        }
    }

    private void validateRecord(RawCostRecord raw) {
        if (raw == null || raw.service() == null || raw.service().isBlank()
                || raw.usageDate() == null || raw.cost() == null || raw.currency() == null
                || raw.currency().isBlank()) {
            throw new IllegalArgumentException("cost record is missing a required field");
        }
    }

    private String currency(String currency) {
        return currency.trim().toUpperCase();
    }

    private String sourceId(RawCostRecord raw) {
        return raw.usageDate() + ":" + raw.service() + ":" + (raw.region() == null ? "" : raw.region());
    }
}
