package org.aezden.backend.ingestion.provider;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.aezden.backend.cost.Provider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AzureCostProvider implements CloudCostProvider {
    private final RestClient.Builder restClientBuilder;

    @Value("${azure.cost-management.endpoint:https://management.azure.com}")
    private String endpoint;
    @Value("${azure.cost-management.access-token:}")
    private String accessToken;

    @Override
    public Provider provider() {
        return Provider.AZURE;
    }

    @Override
    public List<RawCostRecord> fetchCosts(String accountId, LocalDate startDate, LocalDate endDate) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("Azure Cost Management requires AZURE_COST_MANAGEMENT_ACCESS_TOKEN");
        }

        Map<String, Object> body = Map.of(
                "type", "ActualCost",
                "timeframe", "Custom",
                "timePeriod", Map.of("from", startDate + "T00:00:00Z", "to", endDate + "T00:00:00Z"),
                "dataset", Map.of(
                        "granularity", "Daily",
                        "aggregation", Map.of("totalCost", Map.of("name", "PreTaxCost", "function", "Sum")),
                        "grouping", List.of(
                                Map.of("type", "Dimension", "name", "ServiceName"),
                                Map.of("type", "Dimension", "name", "ResourceLocation"))));

        JsonNode response = restClientBuilder.baseUrl(endpoint).build().post()
                .uri("/subscriptions/{subscription}/providers/Microsoft.CostManagement/query"
                        + "?api-version=2023-03-01", accountId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        return parseRows(response);
    }

    private List<RawCostRecord> parseRows(JsonNode response) {
        JsonNode properties = response.path("properties");
        List<String> columns = new ArrayList<>();
        properties.path("columns").forEach(column -> columns.add(column.path("name").asText()));
        List<RawCostRecord> records = new ArrayList<>();
        for (JsonNode row : properties.path("rows")) {
            String date = value(row, columns, "UsageDate");
            if (date == null) {
                date = value(row, columns, "UsageDateTime");
            }
            records.add(new RawCostRecord(
                    required(value(row, columns, "ServiceName"), "ServiceName"),
                    blankToNull(value(row, columns, "ResourceLocation")),
                    LocalDate.parse(date.substring(0, 10)),
                    new BigDecimal(required(value(row, columns, "PreTaxCost"), "PreTaxCost")),
                    required(value(row, columns, "Currency"), "Currency")));
        }
        return records;
    }

    private String value(JsonNode row, List<String> columns, String name) {
        int index = columns.indexOf(name);
        return index < 0 || row.size() <= index || row.get(index).isNull()
                ? null : row.get(index).asText();
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Azure cost response is missing field " + field);
        }
        return value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
