package rmn.insights.repository;

import rmn.insights.dto.AggregationResult;
import rmn.insights.dto.MetricType;
import rmn.insights.dto.TimeRange;

public interface IHistoricalRepository {

    AggregationResult query(String tenantId, String campaignId, MetricType metric, TimeRange range);
}
