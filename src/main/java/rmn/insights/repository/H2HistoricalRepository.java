package rmn.insights.repository;

import rmn.insights.dto.AggregationResult;
import rmn.insights.dto.MetricPoint;
import rmn.insights.dto.MetricType;
import rmn.insights.dto.TimeRange;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Log4j2
@Profile("local")
@Repository
public class H2HistoricalRepository implements IHistoricalRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public H2HistoricalRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AggregationResult query(String tenantId, String campaignId,
                                   MetricType metric, TimeRange range) {
        String sql = """
                SELECT metric_hour, SUM(%s) AS metric_value, SUM(unique_users) AS unique_users
                FROM campaign_metrics_hourly
                WHERE tenant_id = :tenantId AND campaign_id = :campaignId
                  AND metric_hour >= :start AND metric_hour < :end
                GROUP BY metric_hour ORDER BY metric_hour
                """.formatted(metric.getColumnName());
        Map<String, Object> params = Map.of(
                "tenantId", tenantId,
                "campaignId", campaignId,
                "start", Timestamp.from(range.start()),
                "end", Timestamp.from(range.end())
        );
        log.debug("H2 historical query metric={} start={} end={} campaign={}", metric, range.start(), range.end(), campaignId);
        List<MetricPoint> timeSeries = new ArrayList<>();
        long total = 0;
        long uniqueUsers = 0;
        List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
        for (Map<String, Object> row : rows) {
            Timestamp ts = (Timestamp) row.get("metric_hour");
            long val = ((Number) row.get("metric_value")).longValue();
            long uniq = ((Number) row.get("unique_users")).longValue();
            total += val;
            uniqueUsers = Math.max(uniqueUsers, uniq);
            timeSeries.add(new MetricPoint(ts.toInstant(), val));
        }
        log.debug("H2 historical result campaign={} rows={} total={}", campaignId, rows.size(), total);
        return new AggregationResult(total, uniqueUsers, timeSeries);
    }
}
