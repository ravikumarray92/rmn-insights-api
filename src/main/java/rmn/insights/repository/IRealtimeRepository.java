package rmn.insights.repository;

import rmn.insights.dto.AggregationResult;
import rmn.insights.dto.MetricType;
import rmn.insights.dto.TimeRange;

public interface IRealtimeRepository {

    Long getCounter(String tenantId, String campaignId, MetricType metric);

    long getUniqueCount(String tenantId, String campaignId, String hllSuffix);

    AggregationResult queryDruid(String tenantId, String campaignId, MetricType metric, TimeRange range);
}
