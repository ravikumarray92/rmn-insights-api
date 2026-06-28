package rmn.insights.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImpressionsResponse(
        String campaignId,
        String tenantId,
        long totalImpressions,
        long uniqueReach,
        List<MetricPoint> timeSeries,
        TimeRangeDto queryWindow,
        int dataFreshnessSeconds
) {}
