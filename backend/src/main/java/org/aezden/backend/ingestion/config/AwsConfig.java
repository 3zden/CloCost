package org.aezden.backend.ingestion.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import org.springframework.web.client.RestClient;

@Configuration
public class AwsConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public CostExplorerClient costExplorerClient(
            @Value("${aws.region:us-east-1}") String region
    ) {
        return CostExplorerClient.builder()
                .region(Region.of(region))
                .build();
    }
}
