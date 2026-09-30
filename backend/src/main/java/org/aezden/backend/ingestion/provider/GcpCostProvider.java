package org.aezden.backend.ingestion.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class GcpCostProvider implements CloudCostProvider {
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    @Value("${gcp.billing.endpoint:https://bigquery.googleapis.com}")
    private String endpoint;
    @Value("${gcp.billing.project:}")
    private String billingProject;
    @Value("${gcp.billing.dataset:}")
    private String billingDataset;
    @Value("${gcp.billing.table:}")
    private String billingTable;
    @Value("${gcp.billing.access-token:}")
    private String accessToken;

    @Override
    public Provider provider() {
        return Provider.GCP;
    }

    @Override
    public List<RawCostRecord> fetchCosts(String accountId, LocalDate startDate, LocalDate endDate) {
        requireConfiguration();
        String query = """
                SELECT
                  DATE(usage_start_time) AS usage_date,
                  service.description AS service,
                  location.region AS region,
                  SUM(cost) AS cost,
                  ANY_VALUE(currency) AS currency
                FROM `%s.%s`
                WHERE DATE(usage_start_time) >= @start_date
                  AND DATE(usage_start_time) < @end_date
                GROUP BY usage_date, service, region
                ORDER BY usage_date
                """.formatted(billingDataset, billingTable);

        RestClient client = restClientBuilder.baseUrl(endpoint).build();
        JsonNode response = client.post()
                .uri("/bigquery/v2/projects/{project}/queries", billingProject)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "query", query,
                        "useLegacySql", false,
                        "parameterMode", "NAMED",
                        "queryParameters", List.of(
                                queryParameter("start_date", startDate.toString()),
                                queryParameter("end_date", endDate.toString()))))
                .retrieve()
                .body(JsonNode.class);
        return parseRows(response);
    }

    private Map<String, Object> queryParameter(String name, String value) {
        return Map.of("name", name, "parameterType", Map.of("type", "DATE"),
                "parameterValue", Map.of("value", value));
    }

    private List<RawCostRecord> parseRows(JsonNode response) {
        List<RawCostRecord> records = new ArrayList<>();
        JsonNode fields = response.path("schema").path("fields");
        for (JsonNode row : response.path("rows")) {
            JsonNode values = row.path("f");
            String date = value(values, fields, "usage_date");
            records.add(new RawCostRecord(
                    value(values, fields, "service"),
                    blankToNull(value(values, fields, "region")),
                    LocalDate.parse(date),
                    new BigDecimal(value(values, fields, "cost")),
                    value(values, fields, "currency")));
        }
        return records;
    }

    private String value(JsonNode values, JsonNode fields, String fieldName) {
        for (int i = 0; i < fields.size(); i++) {
            if (fieldName.equals(fields.get(i).path("name").asText())) {
                return values.get(i).path("v").asText();
            }
        }
        throw new IllegalStateException("GCP billing response is missing field " + fieldName);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void requireConfiguration() {
        if (isBlank(billingProject) || isBlank(billingDataset) || isBlank(billingTable)
                || isBlank(accessToken)) {
            throw new IllegalStateException(
                    "GCP billing requires GCP_BILLING_PROJECT, GCP_BILLING_DATASET, "
                            + "GCP_BILLING_TABLE and GCP_BILLING_ACCESS_TOKEN");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
