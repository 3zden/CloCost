package org.aezden.ingestionservice.Provider;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public interface BillingProvider {
    List<Object> fetchCosts(
            CloudAccount cloudAccount,
            Instant from,
            Instant to
    );
}
