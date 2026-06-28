package rmn.insights.dto;

import java.util.List;

public record AggregationResult(long total, long uniqueUsers, List<MetricPoint> timeSeries) {}
