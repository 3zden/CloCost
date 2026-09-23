package org.aezden.backend.cost;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
public class CostDataInitializer implements CommandLineRunner {

    private static final UUID PLATFORM_ACCOUNT =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ANALYTICS_ACCOUNT =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final CostRepo costRepo;
    private final boolean seedDataEnabled;

    public CostDataInitializer(
            CostRepo costRepo,
            @Value("${app.seed-data.enabled:true}") boolean seedDataEnabled
    ) {
        this.costRepo = costRepo;
        this.seedDataEnabled = seedDataEnabled;
    }

    @Override
    public void run(String... args) {
        if (!seedDataEnabled || costRepo.count() > 0) {
            return;
        }

        LocalDate today = LocalDate.now();
        costRepo.saveAll(List.of(
                cost(PLATFORM_ACCOUNT, Provider.AWS, "EC2", "us-east-1",
                        today.minusDays(4), "142.38"),
                cost(PLATFORM_ACCOUNT, Provider.AWS, "S3", "us-east-1",
                        today.minusDays(3), "28.74"),
                cost(PLATFORM_ACCOUNT, Provider.GCP, "BigQuery", "us-central1",
                        today.minusDays(2), "86.19"),
                cost(PLATFORM_ACCOUNT, Provider.AZURE, "App Service", "westeurope",
                        today.minusDays(1), "64.52"),
                cost(ANALYTICS_ACCOUNT, Provider.AWS, "RDS", "eu-west-1",
                        today.minusDays(4), "97.11"),
                cost(ANALYTICS_ACCOUNT, Provider.GCP, "Cloud Storage", "europe-west1",
                        today.minusDays(2), "19.43"),
                cost(ANALYTICS_ACCOUNT, Provider.ORACLE, "Compute", "eu-frankfurt-1",
                        today.minusDays(1), "51.80")
        ));
    }

    private CostRecord cost(
            UUID accountId,
            Provider provider,
            String service,
            String region,
            LocalDate usageDate,
            String amount
    ) {
        CostRecord record = new CostRecord();
        record.setAccountId(accountId);
        record.setProvider(provider);
        record.setService(service);
        record.setRegion(region);
        record.setUsageDate(usageDate);
        record.setCost(new BigDecimal(amount));
        record.setCurrency("USD");
        return record;
    }
}
