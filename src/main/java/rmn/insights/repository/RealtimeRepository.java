package rmn.insights.repository;

import rmn.insights.dto.AggregationResult;
import rmn.insights.dto.MetricPoint;
import rmn.insights.dto.MetricType;
import rmn.insights.dto.TimeRange;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Log4j2
@Profile("!local")
@Repository
public class RealtimeRepository implements IRealtimeRepository {

    private final StringRedisTemplate redisTemplate;
    private final RestClient druidClient;
    private final ObjectMapper objectMapper;

    public RealtimeRepository(StringRedisTemplate redisTemplate,
                              @Qualifier("druidRestClient") RestClient druidClient,
                              ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.druidClient = druidClient;
        this.objectMapper = objectMapper;
    }

    // Redis key builder: rmn:{tenantId}:campaign:{campaignId}:{suffix}
    private String key(String tenantId, String campaignId, String suffix) {
        return "rmn:" + tenantId + ":campaign:" + campaignId + ":" + suffix;
    }

    public Long getCounter(String tenantId, String campaignId, MetricType metric) {
        String val = redisTemplate.opsForValue().get(key(tenantId, campaignId, metric.getColumnName()));
        return val != null ? Long.parseLong(val) : null;
    }

    public long getUniqueCount(String tenantId, String campaignId, String hllSuffix) {
        Long count = redisTemplate.opsForHyperLogLog().size(key(tenantId, campaignId, hllSuffix));
        return count != null ? count : 0L;
    }

    public AggregationResult queryDruid(String tenantId, String campaignId,
                                        MetricType metric, TimeRange range) {
        // Build Druid timeseries query as Map
        Map<String, Object> filter = Map.of(
                "type", "and",
                "fields", List.of(
                        Map.of("type", "selector", "dimension", "tenant_id", "value", tenantId),
                        Map.of("type", "selector", "dimension", "campaign_id", "value", campaignId)
                )
        );
        List<Map<String, Object>> aggregations = List.of(
                Map.of("type", "longSum", "name", "metric_value", "fieldName", metric.getColumnName()),
                Map.of("type", "longSum", "name", "unique_users", "fieldName", "unique_users_hll")
        );
        String interval = range.start() + "/" + range.end();
        log.debug("druid query metric={} interval={} campaign={}", metric, interval, campaignId);
        Map<String, Object> query = Map.of(
                "queryType", "timeseries",
                "dataSource", "rmn_campaign_metrics",
                "granularity", "hour",
                "filter", filter,
                "aggregations", aggregations,
                "intervals", List.of(interval)
        );

        // POST to Druid and parse response
        String responseBody = druidClient.post()
                .uri("/druid/v2")
                .contentType(MediaType.APPLICATION_JSON)
                .body(query)
                .retrieve()
                .body(String.class);

        // Parse List<{timestamp, result:{metric_value, unique_users}}>
        try {
            JsonNode rows = objectMapper.readTree(responseBody);
            List<MetricPoint> timeSeries = new ArrayList<>();
            long total = 0;
            long uniqueUsers = 0;
            for (JsonNode row : rows) {
                Instant ts = Instant.parse(row.get("timestamp").asText());
                JsonNode result = row.get("result");
                long val = result.path("metric_value").asLong(0);
                total += val;
                uniqueUsers = Math.max(uniqueUsers, result.path("unique_users").asLong(0));
                timeSeries.add(new MetricPoint(ts, val));
            }
            log.debug("druid result campaign={} total={} uniqueUsers={} points={}", campaignId, total, uniqueUsers, timeSeries.size());
            return new AggregationResult(total, uniqueUsers, timeSeries);
        } catch (Exception e) {
            log.error("Failed to parse Druid response campaign={}", campaignId, e);
            throw new RuntimeException("Failed to parse Druid response", e);
        }
    }
}
