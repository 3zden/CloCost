package org.aezden.ingestionservice.Provider.aws;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;

@Configuration
public class AwsClientConfig {

    @Bean
    public CostExplorerClient costExplorerClient(){
        return CostExplorerClient.builder()
                .build();
    }
}
