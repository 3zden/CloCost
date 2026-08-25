package org.aezden.ingestionservice.Provider.aws;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;

@Configuration
public class AwsClientConfig {

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Bean
    public CostExplorerClient costExplorerClient(){
        return CostExplorerClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }
}
