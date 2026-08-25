package org.aezden.ingestionservice.Provider.aws;

import org.aezden.ingestionservice.Account.CloudAccount;
import org.aezden.ingestionservice.Provider.BillingProvider;

import java.time.Instant;
import java.util.List;

public class AwsBillingProvider implements BillingProvider {

    @Override
    public List<Object> fetchCosts(CloudAccount cloudAccount, Instant from, Instant to) {
        throw new UnsupportedOperationException("AWS cost ingestion is not implemented yet");
    }
}
